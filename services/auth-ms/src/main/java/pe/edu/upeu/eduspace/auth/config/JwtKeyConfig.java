package pe.edu.upeu.eduspace.auth.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Claves RSA de firma. PROD: se cargan de archivos PEM y, si faltan, el servicio NO arranca (una clave generada al
 * vuelo invalidaría todos los tokens en cada reinicio y las réplicas firmarían con claves distintas). DEV: con
 * {@code generar-clave-si-falta=true} se genera un par RSA 2048 al arrancar. Rotación: la clave pública anterior se
 * sigue publicando en el JWKS.
 */
@Slf4j
@Configuration
public class JwtKeyConfig {

    @Bean
    public RSAKey claveActual(AuthProperties props) throws Exception {
        AuthProperties.Jwt cfg = props.getJwt();
        boolean hayArchivos = !cfg.getPrivateKeyPath().isBlank() && !cfg.getPublicKeyPath().isBlank();
        if (hayArchivos) {
            RSAPrivateKey privada = (RSAPrivateKey) KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(leerPem(cfg.getPrivateKeyPath())));
            RSAPublicKey publica = (RSAPublicKey) KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(leerPem(cfg.getPublicKeyPath())));
            log.info("Clave JWT cargada desde archivo (kid={})", cfg.getKid());
            return new RSAKey.Builder(publica).privateKey(privada).keyID(cfg.getKid()).build();
        }
        if (!cfg.isGenerarClaveSiFalta()) {
            throw new IllegalStateException("No se configuraron las claves JWT (app.auth.jwt.private-key-path / public-key-path). "
                + "En PROD son obligatorias; en DEV use app.auth.jwt.generar-clave-si-falta=true.");
        }
        // DEV: el par generado se guarda en un archivo y se reutiliza en cada arranque. Sin esto, cada reinicio de auth-ms cambiaba
        // la clave: el Gateway y los servicios (que la guardan en caché) rechazaban todos los tokens y la web quedaba con sesión
        // "viva" pero sin datos. Para empezar de cero basta borrar el archivo.
        Path archivo = Path.of(System.getProperty("user.home"), ".eduspace-dev", "jwt-rsa-dev.der");
        KeyPair par = leerParDev(archivo);
        if (par == null) {
            KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
            g.initialize(2048);
            par = g.generateKeyPair();
            guardarParDev(archivo, par);
            log.warn("Clave JWT de DEV GENERADA y guardada en {} (kid={}). Solo para DEV.", archivo, cfg.getKid());
        } else {
            log.warn("Clave JWT de DEV reutilizada desde {} (kid={}): los tokens sobreviven a los reinicios. Solo para DEV.", archivo, cfg.getKid());
        }
        return new RSAKey.Builder((RSAPublicKey) par.getPublic()).privateKey(par.getPrivate()).keyID(cfg.getKid()).build();
    }

    private static KeyPair leerParDev(Path archivo) {
        try {
            if (!Files.exists(archivo)) return null;
            byte[] datos = Files.readAllBytes(archivo);
            int n = java.nio.ByteBuffer.wrap(datos, 0, 4).getInt();
            byte[] priv = java.util.Arrays.copyOfRange(datos, 4, 4 + n);
            byte[] pub = java.util.Arrays.copyOfRange(datos, 4 + n, datos.length);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return new KeyPair(kf.generatePublic(new X509EncodedKeySpec(pub)), kf.generatePrivate(new PKCS8EncodedKeySpec(priv)));
        } catch (Exception e) {
            log.warn("No se pudo leer la clave de DEV guardada ({}); se genera una nueva.", e.getMessage());
            return null;
        }
    }

    private static void guardarParDev(Path archivo, KeyPair par) {
        try {
            Files.createDirectories(archivo.getParent());
            byte[] priv = par.getPrivate().getEncoded();
            byte[] pub = par.getPublic().getEncoded();
            java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(4 + priv.length + pub.length);
            b.putInt(priv.length).put(priv).put(pub);
            Files.write(archivo, b.array());
            try {
                Files.setPosixFilePermissions(archivo, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
            } catch (UnsupportedOperationException ignorado) { /* sistema sin permisos POSIX */ }
        } catch (IOException e) {
            log.warn("No se pudo guardar la clave de DEV ({}): los tokens no sobrevivirán a un reinicio.", e.getMessage());
        }
    }

    /** JWKS público: clave actual y, si existe, la anterior (rotación). */
    @Bean
    public JWKSet jwksPublico(RSAKey claveActual, AuthProperties props) throws Exception {
        List<com.nimbusds.jose.jwk.JWK> claves = new ArrayList<>();
        claves.add(claveActual.toPublicJWK());
        String anterior = props.getJwt().getPublicKeyAnteriorPath();
        if (anterior != null && !anterior.isBlank()) {
            RSAPublicKey pub = (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(leerPem(anterior)));
            claves.add(new RSAKey.Builder(pub).keyID(props.getJwt().getKidAnterior()).build());
        }
        return new JWKSet(claves);
    }

    @Bean
    public JwtEncoder jwtEncoder(RSAKey claveActual) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(claveActual)));
    }

    /** Valida los tokens propios (endpoints autenticados de auth-ms, p. ej. cambiar-clave) con las claves públicas. */
    @Bean
    public JwtDecoder jwtDecoder(RSAKey claveActual) throws Exception {
        return NimbusJwtDecoder.withPublicKey(claveActual.toRSAPublicKey()).build();
    }

    private static byte[] leerPem(String ruta) throws IOException {
        String pem = Files.readString(Path.of(ruta));
        String base64 = pem.replaceAll("-----(BEGIN|END)[A-Z ]+-----", "").replaceAll("\\s", "");
        return Base64.getDecoder().decode(base64);
    }
}
