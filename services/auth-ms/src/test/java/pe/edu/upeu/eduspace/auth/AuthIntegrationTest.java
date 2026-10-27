package pe.edu.upeu.eduspace.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.SignedJWT;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import pe.edu.upeu.eduspace.auth.config.AuthProperties;
import pe.edu.upeu.eduspace.auth.config.JwtKeyConfig;

@AutoConfigureMockMvc
class AuthIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void limpiar() {
        // se conservan las semillas (ids 1..3) y se borra lo creado por las pruebas
        jdbc.execute("DELETE FROM usuarios WHERE username NOT IN ('admin','secretaria','cajero')");
        jdbc.execute("DELETE FROM refresh_tokens");
        jdbc.execute("UPDATE usuarios SET activo = true, intentos_fallidos = 0, bloqueado_hasta = NULL, debe_cambiar_clave = false, "
            + "password_hash = CASE username WHEN 'admin' THEN '$2a$10$ebVtXreOH4bTEgSTfpDywukPpMAA2Oi/rEPZDfGeAundF2Svo.2LW' "
            + "WHEN 'secretaria' THEN '$2a$10$QPUjvSxAGninK2gjeNrb3eWHsJjHpM6z14Dxp3UerqXyFRCML3dki' "
            + "ELSE '$2a$10$X33bg2AlguvapRMiEuHOAeA7RvlKyeMrA/sj8UkG2ALdM2zJudB5W' END");
        jdbc.execute("DELETE FROM usuario_roles WHERE usuario_id IN (SELECT id FROM usuarios WHERE username = 'admin')");
        jdbc.execute("INSERT INTO usuario_roles SELECT u.id, r.id FROM usuarios u, roles r WHERE u.username = 'admin' AND r.nombre = 'ADMIN'");
    }

    ResultActions login(String user, String clave) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(user, clave)));
    }

    String json(ResultActions r) throws Exception {
        return r.andReturn().getResponse().getContentAsString();
    }

    String tokenDe(String user, String clave) throws Exception {
        return JsonPath.read(json(login(user, clave).andExpect(status().isOk())), "$.access_token");
    }

    String bearer(String user, String clave) throws Exception {
        return "Bearer " + tokenDe(user, clave);
    }

    // ------------------------------------------------------------------ login y JWT

    @Test
    void lasTresSemillasDevInicianSesion() throws Exception {
        login("admin", "admin123").andExpect(status().isOk()).andExpect(jsonPath("$.token_type").value("Bearer"))
            .andExpect(jsonPath("$.expires_in").value(1800)).andExpect(jsonPath("$.refresh_token").isNotEmpty());
        login("secretaria", "secre123").andExpect(status().isOk());
        login("cajero", "cajero123").andExpect(status().isOk());
        login("ADMIN@eduspace.com", "admin123").andExpect(status().isOk());       // también por correo, sin distinguir mayúsculas
    }

    @Test
    void elTokenTraeElContratoYSeValidaConElJwks() throws Exception {
        String token = tokenDe("secretaria", "secre123");
        String jwks = json(mvc.perform(get("/.well-known/jwks.json")).andExpect(status().isOk()));
        assertThat(jwks).doesNotContain("\"d\"").doesNotContain("\"p\"").doesNotContain("\"q\"");   // nunca la parte privada
        RSAKey publica = (RSAKey) JWKSet.parse(jwks).getKeys().get(0);
        SignedJWT jwt = SignedJWT.parse(token);
        assertThat(jwt.verify(new RSASSAVerifier(publica))).isTrue();
        assertThat(jwt.getHeader().getKeyID()).isEqualTo(publica.getKeyID());
        assertThat(jwt.getHeader().getAlgorithm().getName()).isEqualTo("RS256");
        Map<String, Object> c = jwt.getJWTClaimsSet().getClaims();
        assertThat(c).containsEntry("preferred_username", "secretaria").containsEntry("email", "secretaria@eduspace.com")
            .containsKeys("sub", "iss", "iat", "exp", "usuarioId");
        assertThat(((Number) c.get("usuarioId")).longValue()).isEqualTo(Long.parseLong(c.get("sub").toString()));
        assertThat((Map<String, Object>) c.get("realm_access")).containsEntry("roles", List.of("SECRETARIA"));   // sin prefijo ROLE_
    }

    @Test
    void usuarioInexistenteYClaveErroneaDevuelvenElMismoError() throws Exception {
        String a = json(login("fantasma", "x1234567").andExpect(status().isUnauthorized()));
        String b = json(login("admin", "incorrecta1").andExpect(status().isUnauthorized()));
        assertThat((String) JsonPath.read(a, "$.mensaje")).isEqualTo(JsonPath.read(b, "$.mensaje"));
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"\"}")).andExpect(status().isBadRequest());
    }

    @Test
    void cincoIntentosFallidosBloquean15MinutosAunqueLaClaveSeaCorrecta() throws Exception {
        for (int i = 0; i < 5; i++) login("cajero", "mala-clave1").andExpect(status().isUnauthorized());
        login("cajero", "cajero123").andExpect(status().isLocked());
        assertThat(jdbc.queryForObject("select bloqueado_hasta > now() + interval '14 minutes' from usuarios where username = 'cajero'", Boolean.class)).isTrue();
        // el administrador lo desbloquea
        Long id = jdbc.queryForObject("select id from usuarios where username = 'cajero'", Long.class);
        mvc.perform(patch("/api/v1/auth/usuarios/" + id + "/desbloquear").header("Authorization", bearer("admin", "admin123"))).andExpect(status().isOk());
        login("cajero", "cajero123").andExpect(status().isOk());
    }

    @Test
    void unLoginCorrectoReiniciaLosIntentos() throws Exception {
        for (int i = 0; i < 4; i++) login("cajero", "mala-clave1").andExpect(status().isUnauthorized());
        login("cajero", "cajero123").andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select intentos_fallidos from usuarios where username = 'cajero'", Integer.class)).isZero();
        for (int i = 0; i < 4; i++) login("cajero", "mala-clave1").andExpect(status().isUnauthorized());
        login("cajero", "cajero123").andExpect(status().isOk());      // 4 + 4 no bloquean porque hubo un acierto en medio
    }

    // ------------------------------------------------------------------ refresh y logout

    @Test
    void refreshRotaElTokenYUnoRevocadoCierraTodasLasSesiones() throws Exception {
        String r1 = JsonPath.read(json(login("admin", "admin123")), "$.refresh_token");
        assertThat(jdbc.queryForObject("select token_hash from refresh_tokens", String.class)).isNotEqualTo(r1).hasSize(64);   // solo se guarda el hash
        String cuerpo = json(mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
            .content("{\"refresh_token\":\"%s\"}".formatted(r1))).andExpect(status().isOk()));
        String r2 = JsonPath.read(cuerpo, "$.refresh_token");
        assertThat(r2).isNotEqualTo(r1);
        // reutilizar el viejo: 401 y se revoca la sesión nueva también (posible robo)
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content("{\"refresh_token\":\"%s\"}".formatted(r1)))
            .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content("{\"refresh_token\":\"%s\"}".formatted(r2)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevocaElRefreshToken() throws Exception {
        String r = JsonPath.read(json(login("admin", "admin123")), "$.refresh_token");
        mvc.perform(post("/api/v1/auth/logout").contentType(MediaType.APPLICATION_JSON).content("{\"refresh_token\":\"%s\"}".formatted(r)))
            .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content("{\"refresh_token\":\"%s\"}".formatted(r)))
            .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content("{\"refresh_token\":\"inventado\"}"))
            .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ cambio de clave

    @Test
    void cambiarClaveValidaReglasYCierraLasOtrasSesiones() throws Exception {
        String refresh = JsonPath.read(json(login("secretaria", "secre123")), "$.refresh_token");
        String auth = bearer("secretaria", "secre123");
        String url = "/api/v1/auth/cambiar-clave";
        mvc.perform(post(url)).andExpect(status().isUnauthorized());
        mvc.perform(post(url).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
            .content("{\"claveActual\":\"equivocada1\",\"claveNueva\":\"NuevaClave1\"}")).andExpect(status().isConflict());
        mvc.perform(post(url).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
            .content("{\"claveActual\":\"secre123\",\"claveNueva\":\"corta1\"}")).andExpect(status().isConflict());
        mvc.perform(post(url).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
            .content("{\"claveActual\":\"secre123\",\"claveNueva\":\"soloLetrasAqui\"}")).andExpect(status().isConflict());
        mvc.perform(post(url).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
            .content("{\"claveActual\":\"secre123\",\"claveNueva\":\"secre123\"}")).andExpect(status().isConflict());
        mvc.perform(post(url).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
            .content("{\"claveActual\":\"secre123\",\"claveNueva\":\"NuevaClave1\"}")).andExpect(status().isNoContent());
        login("secretaria", "secre123").andExpect(status().isUnauthorized());
        login("secretaria", "NuevaClave1").andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content("{\"refresh_token\":\"%s\"}".formatted(refresh)))
            .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ administración de usuarios

    @Test
    void soloElAdminAdministraUsuarios() throws Exception {
        mvc.perform(get("/api/v1/auth/usuarios")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/auth/usuarios").header("Authorization", bearer("secretaria", "secre123"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/auth/usuarios").header("Authorization", bearer("admin", "admin123"))).andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElementos").value(3));
    }

    @Test
    void crearUsuarioConClaveTemporalQueDebeCambiarse() throws Exception {
        String admin = bearer("admin", "admin123");
        String cuerpo = json(mvc.perform(post("/api/v1/auth/usuarios").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"prof.diaz\",\"email\":\"diaz@colegio.pe\",\"nombres\":\"Luis\",\"apellidos\":\"Díaz\",\"roles\":[\"DOCENTE\"]}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.usuario.debeCambiarClave").value(true))
            .andExpect(jsonPath("$.usuario.roles[0]").value("DOCENTE")));
        String temporal = JsonPath.read(cuerpo, "$.claveTemporal");
        assertThat(temporal).hasSizeGreaterThanOrEqualTo(8);
        assertThat(cuerpo).doesNotContain("password").doesNotContain("$2a$");          // el hash nunca se expone
        login("prof.diaz", temporal).andExpect(status().isOk()).andExpect(jsonPath("$.debe_cambiar_clave").value(true));
        Long id = ((Number) JsonPath.read(cuerpo, "$.usuario.id")).longValue();
        mvc.perform(get("/api/v1/auth/usuarios/" + id).header("Authorization", admin)).andExpect(status().isOk())
            .andExpect(jsonPath("$.ultimoLogin").isNotEmpty());
    }

    @Test
    void validacionesDeAltaYDuplicados() throws Exception {
        String admin = bearer("admin", "admin123");
        String base = "{\"username\":\"%s\",\"email\":\"%s\",\"nombres\":\"A\",\"apellidos\":\"B\",\"roles\":%s%s}";
        mvc.perform(post("/api/v1/auth/usuarios").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
            .content(base.formatted("admin", "otro@x.pe", "[\"DOCENTE\"]", ""))).andExpect(status().isConflict());          // username duplicado
        mvc.perform(post("/api/v1/auth/usuarios").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
            .content(base.formatted("nuevo", "ADMIN@eduspace.com", "[\"DOCENTE\"]", ""))).andExpect(status().isConflict());   // correo duplicado
        mvc.perform(post("/api/v1/auth/usuarios").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
            .content(base.formatted("nuevo", "n@x.pe", "[\"SUPERHEROE\"]", ""))).andExpect(status().isConflict());            // rol inexistente
        mvc.perform(post("/api/v1/auth/usuarios").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
            .content(base.formatted("nuevo", "n@x.pe", "[]", ""))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/auth/usuarios").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
            .content(base.formatted("nuevo", "n@x.pe", "[\"DOCENTE\"]", ",\"clave\":\"debil\""))).andExpect(status().isConflict());   // clave débil
        mvc.perform(post("/api/v1/auth/usuarios").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
            .content(base.formatted("con espacio", "n@x.pe", "[\"DOCENTE\"]", ""))).andExpect(status().isBadRequest());
    }

    @Test
    void elUltimoAdminNoSePuedeQuitarNiDesactivar() throws Exception {
        String admin = bearer("admin", "admin123");
        Long id = jdbc.queryForObject("select id from usuarios where username = 'admin'", Long.class);
        mvc.perform(put("/api/v1/auth/usuarios/" + id + "/roles").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
            .content("{\"roles\":[\"DOCENTE\"]}")).andExpect(status().isConflict());
        mvc.perform(patch("/api/v1/auth/usuarios/" + id + "/desactivar").header("Authorization", admin)).andExpect(status().isConflict());
        mvc.perform(delete("/api/v1/auth/usuarios/" + id).header("Authorization", admin)).andExpect(status().isConflict());
        // con un segundo ADMIN activo sí se puede
        String otro = json(mvc.perform(post("/api/v1/auth/usuarios").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"admin2\",\"email\":\"a2@x.pe\",\"nombres\":\"A\",\"apellidos\":\"B\",\"roles\":[\"ADMIN\"],\"clave\":\"Clave12345\"}")));
        mvc.perform(patch("/api/v1/auth/usuarios/" + id + "/desactivar").header("Authorization", admin)).andExpect(status().isOk());
        login("admin", "admin123").andExpect(status().isUnauthorized());           // un usuario inactivo no entra
        assertThat(otro).contains("admin2");
    }

    @Test
    void cambiarRolesYDesactivarCierranLasSesionesAbiertas() throws Exception {
        String admin = bearer("admin", "admin123");
        String refresh = JsonPath.read(json(login("cajero", "cajero123")), "$.refresh_token");
        Long id = jdbc.queryForObject("select id from usuarios where username = 'cajero'", Long.class);
        mvc.perform(put("/api/v1/auth/usuarios/" + id + "/roles").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
            .content("{\"roles\":[\"cajero\",\"SECRETARIA\"]}")).andExpect(status().isOk()).andExpect(jsonPath("$.roles.length()").value(2));
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content("{\"refresh_token\":\"%s\"}".formatted(refresh)))
            .andExpect(status().isUnauthorized());
        String r2 = JsonPath.read(json(login("cajero", "cajero123")), "$.refresh_token");
        mvc.perform(patch("/api/v1/auth/usuarios/" + id + "/desactivar").header("Authorization", admin)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content("{\"refresh_token\":\"%s\"}".formatted(r2)))
            .andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/v1/auth/usuarios/" + id + "/activar").header("Authorization", admin)).andExpect(status().isOk());
        login("cajero", "cajero123").andExpect(status().isOk());
    }

    @Test
    void restablecerClaveYFiltrarPorRol() throws Exception {
        String admin = bearer("admin", "admin123");
        Long id = jdbc.queryForObject("select id from usuarios where username = 'secretaria'", Long.class);
        String r = json(mvc.perform(post("/api/v1/auth/usuarios/" + id + "/restablecer-clave").header("Authorization", admin)).andExpect(status().isOk()));
        String temporal = JsonPath.read(r, "$.claveTemporal");
        login("secretaria", "secre123").andExpect(status().isUnauthorized());
        login("secretaria", temporal).andExpect(status().isOk()).andExpect(jsonPath("$.debe_cambiar_clave").value(true));
        mvc.perform(get("/api/v1/auth/usuarios").param("rol", "cajero").header("Authorization", admin)).andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElementos").value(1)).andExpect(jsonPath("$.contenido[0].username").value("cajero"));
        mvc.perform(get("/api/v1/auth/usuarios").param("texto", "SECRE").header("Authorization", admin)).andExpect(jsonPath("$.totalElementos").value(1));
        mvc.perform(get("/api/v1/auth/usuarios").param("activo", "false").header("Authorization", admin)).andExpect(jsonPath("$.totalElementos").value(0));
        mvc.perform(get("/api/v1/auth/usuarios/999999").header("Authorization", admin)).andExpect(status().isNotFound());
    }

    @Test
    void sinClavesConfiguradasElServicioNoArranca() {
        AuthProperties props = new AuthProperties();           // sin rutas y sin generar-clave-si-falta (como en PROD)
        assertThatThrownBy(() -> new JwtKeyConfig().claveActual(props)).isInstanceOf(IllegalStateException.class).hasMessageContaining("claves JWT");
    }
}
