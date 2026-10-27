package pe.edu.upeu.eduspace.gateway;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** El Gateway rechaza lo que no trae un JWT RS256 válido de auth-ms antes de enrutar a ningún servicio. */
@SpringBootTest(properties = {
        "spring.cloud.config.enabled=false",
        "spring.config.import=",
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:1/jwks.json",
        "eduspace.gateway.cors.allowed-origins=http://localhost:4200",
        "eduspace.gateway.actuator-public=false",
        "eduspace.gateway.rate-limit.enabled=false"
})
@AutoConfigureMockMvc
@Import(GatewaySecurityTest.Claves.class)
class GatewaySecurityTest {

    static final KeyPair AUTH = par();
    static final KeyPair ATACANTE = par();

    @TestConfiguration
    static class Claves {
        @Bean
        @Primary
        JwtDecoder jwtDecoder() {
            return NimbusJwtDecoder.withPublicKey((RSAPublicKey) AUTH.getPublic()).build();
        }
    }

    @Autowired
    MockMvc mvc;

    static KeyPair par() {
        try {
            KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
            g.initialize(2048);
            return g.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static String token(KeyPair firma, long venceEnMs) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("1").issueTime(new Date()).expirationTime(new Date(System.currentTimeMillis() + venceEnMs))
                .claim("realm_access", java.util.Map.of("roles", java.util.List.of("ADMIN"))).build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        jwt.sign(new RSASSASigner((RSAPrivateKey) firma.getPrivate()));
        return jwt.serialize();
    }

    @Test
    void sinTokenEs401() throws Exception {
        mvc.perform(get("/api/v1/alumnos")).andExpect(status().isUnauthorized());
    }

    @Test
    void tokenFirmadoPorOtraClaveEs401() throws Exception {
        mvc.perform(get("/api/v1/alumnos").header("Authorization", "Bearer " + token(ATACANTE, 60_000)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenConLaFirmaAlteradaEs401() throws Exception {
        String valido = token(AUTH, 60_000);
        String alterado = valido.substring(0, valido.length() - 4) + (valido.endsWith("AAAA") ? "BBBB" : "AAAA");
        mvc.perform(get("/api/v1/alumnos").header("Authorization", "Bearer " + alterado)).andExpect(status().isUnauthorized());
    }

    @Test
    void tokenVencidoEs401() throws Exception {
        mvc.perform(get("/api/v1/alumnos").header("Authorization", "Bearer " + token(AUTH, -120_000)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenValidoPasaElGatewayYNoEs401NiEs403() throws Exception {
        MvcResult r = mvc.perform(get("/api/v1/alumnos").header("Authorization", "Bearer " + token(AUTH, 60_000))).andReturn();
        assertNotEquals(401, r.getResponse().getStatus());
        assertNotEquals(403, r.getResponse().getStatus());
    }

    @Test
    void soloHealthYInfoDeActuatorSonPublicos() throws Exception {
        mvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());
        MvcResult r = mvc.perform(get("/actuator/health")).andReturn();
        assertNotEquals(401, r.getResponse().getStatus());
    }
}
