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
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        KeyPair par = g.generateKeyPair();
        log.warn("Clave JWT GENERADA en memoria (kid={}): los tokens no sobrevivirán a un reinicio. Solo para DEV.", cfg.getKid());
        return new RSAKey.Builder((RSAPublicKey) par.getPublic()).privateKey(par.getPrivate()).keyID(cfg.getKid()).build();
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
