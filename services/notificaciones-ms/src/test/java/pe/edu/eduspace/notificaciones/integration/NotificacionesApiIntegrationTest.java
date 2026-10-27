package pe.edu.eduspace.notificaciones.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import pe.edu.eduspace.notificaciones.service.EmailSender;
import pe.edu.eduspace.notificaciones.service.PushSender;

/** La API de la bandeja: cada usuario solo ve y modifica lo suyo; plantillas y envío manual, solo ADMIN. */
@AutoConfigureMockMvc
class NotificacionesApiIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired LockProvider lockProvider;
    @MockitoBean EmailSender emailSender;
    @MockitoBean PushSender pushSender;

    JwtRequestPostProcessor usuario(long usuarioId, String rol) {
        return jwt().jwt(j -> j.claim("usuarioId", usuarioId).claim("realm_access", Map.of("roles", List.of(rol))))
                .authorities(new SimpleGrantedAuthority("ROLE_" + rol));
    }

    ResultActions enviar(String metodo, String ruta, String cuerpo, JwtRequestPostProcessor quien) throws Exception {
        var req = switch (metodo) {
            case "POST" -> post(ruta);
            case "PUT" -> put(ruta);
            default -> throw new IllegalArgumentException(metodo);
        };
        return mvc.perform(req.with(quien).contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    @BeforeEach
    void datos() {
        jdbc.update("delete from notificaciones");
        jdbc.update("delete from preferencias_notificacion");
        jdbc.update("delete from dispositivos_push");
        reset(pushSender);
        String sql = "insert into notificaciones (usuario_id, tipo, canal, titulo, mensaje, evento_origen, evento_clave, estado, creada_en) values (?, 'PAGO_CONFIRMADO', 'IN_APP', ?, ?, 'pago.confirmado', ?, 'ENVIADA', now() - ?::interval)";
        jdbc.update(sql, 500L, "Primera", "msg 1", "1", "3 hours");
        jdbc.update(sql, 500L, "Segunda", "msg 2", "2", "2 hours");
        jdbc.update(sql, 500L, "Tercera", "msg 3", "3", "1 hour");
        jdbc.update(sql, 600L, "De otro usuario", "ajena", "4", "1 hour");
    }

    long idDe(String titulo) {
        return jdbc.queryForObject("select id from notificaciones where titulo = ?", Long.class, titulo);
    }

    @Test
    void sinTokenEs401() throws Exception {
        mvc.perform(get("/api/v1/notificaciones/mias")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/notificaciones/mias").with(jwt().jwt(j -> j.claim("sub", "x")))).andExpect(status().isForbidden());   // sin claim usuarioId
    }

    @Test
    void laBandejaEsPaginadaOrdenadaYSoloDelUsuario() throws Exception {
        mvc.perform(get("/api/v1/notificaciones/mias").with(usuario(500, "APODERADO")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.contenido[0].titulo").value("Tercera"))     // la más reciente primero
                .andExpect(jsonPath("$.contenido[2].titulo").value("Primera"));
        mvc.perform(get("/api/v1/notificaciones/mias?size=2&page=1").with(usuario(500, "APODERADO")))
                .andExpect(jsonPath("$.contenido", hasSize(1))).andExpect(jsonPath("$.totalPaginas").value(2));
        mvc.perform(get("/api/v1/notificaciones/mias").with(usuario(700, "ALUMNO")))
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void marcarLeidasYContarNoLeidas() throws Exception {
        var yo = usuario(500, "APODERADO");
        mvc.perform(get("/api/v1/notificaciones/mias/no-leidas/conteo").with(yo)).andExpect(jsonPath("$.noLeidas").value(3));
        mvc.perform(patch("/api/v1/notificaciones/" + idDe("Primera") + "/leida").with(yo))
                .andExpect(status().isOk()).andExpect(jsonPath("$.leida").value(true)).andExpect(jsonPath("$.leidaEn").exists());
        mvc.perform(get("/api/v1/notificaciones/mias/no-leidas/conteo").with(yo)).andExpect(jsonPath("$.noLeidas").value(2));
        mvc.perform(get("/api/v1/notificaciones/mias?leida=false").with(yo)).andExpect(jsonPath("$.total").value(2));
        mvc.perform(get("/api/v1/notificaciones/mias?leida=true").with(yo)).andExpect(jsonPath("$.total").value(1));
        mvc.perform(patch("/api/v1/notificaciones/mias/leer-todas").with(yo)).andExpect(jsonPath("$.marcadas").value(2));
        mvc.perform(get("/api/v1/notificaciones/mias/no-leidas/conteo").with(yo)).andExpect(jsonPath("$.noLeidas").value(0));
        // la bandeja de otro usuario no se tocó
        mvc.perform(get("/api/v1/notificaciones/mias/no-leidas/conteo").with(usuario(600, "APODERADO"))).andExpect(jsonPath("$.noLeidas").value(1));
    }

    @Test
    void unUsuarioNoPuedeMarcarNiLeerNotificacionesAjenas() throws Exception {
        long ajena = idDe("De otro usuario");
        mvc.perform(patch("/api/v1/notificaciones/" + ajena + "/leida").with(usuario(500, "APODERADO"))).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("select leida from notificaciones where id = ?", Boolean.class, ajena)).isFalse();
        mvc.perform(patch("/api/v1/notificaciones/999999/leida").with(usuario(500, "APODERADO"))).andExpect(status().isNotFound());
        // tampoco aparece en su bandeja
        mvc.perform(get("/api/v1/notificaciones/mias").with(usuario(500, "APODERADO")))
                .andExpect(jsonPath("$.contenido[*].titulo", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("De otro usuario"))));
    }

    @Test
    void preferenciasPorDefectoTodoHabilitadoYSeGuardanSoloLosCambios() throws Exception {
        var yo = usuario(500, "APODERADO");
        mvc.perform(get("/api/v1/notificaciones/preferencias").with(yo)).andExpect(jsonPath("$", hasSize(0)));
        enviar("PUT", "/api/v1/notificaciones/preferencias",
                "[{\"tipo\":\"PAGO_VENCIDO\",\"canal\":\"EMAIL\",\"habilitada\":false},{\"tipo\":\"TAREA_PUBLICADA\",\"canal\":\"PUSH\",\"habilitada\":false}]", yo)
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)));
        enviar("PUT", "/api/v1/notificaciones/preferencias", "[{\"tipo\":\"PAGO_VENCIDO\",\"canal\":\"EMAIL\",\"habilitada\":true}]", yo)
                .andExpect(jsonPath("$", hasSize(2))).andExpect(jsonPath("$[?(@.tipo=='PAGO_VENCIDO')].habilitada", org.hamcrest.Matchers.hasItem(true)));
        enviar("PUT", "/api/v1/notificaciones/preferencias", "[{\"tipo\":\"\",\"canal\":\"EMAIL\",\"habilitada\":true}]", yo)
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/notificaciones/preferencias").with(usuario(600, "APODERADO"))).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void dispositivosSeRegistranSeReasignanYSeDanDeBaja() throws Exception {
        enviar("POST", "/api/v1/notificaciones/dispositivos", "{\"token\":\"tok-abc\",\"plataforma\":\"ANDROID\"}", usuario(500, "APODERADO"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.activo").value(true));
        // el teléfono cambió de dueño: el token se reasigna, no se duplica
        enviar("POST", "/api/v1/notificaciones/dispositivos", "{\"token\":\"tok-abc\",\"plataforma\":\"IOS\"}", usuario(600, "APODERADO"))
                .andExpect(status().isCreated());
        assertThat(jdbc.queryForList("select usuario_id, plataforma from dispositivos_push")).singleElement()
                .satisfies(f -> assertThat(f).containsEntry("usuario_id", 600L).containsEntry("plataforma", "IOS"));
        // quien no es dueño del token no puede darlo de baja
        mvc.perform(delete("/api/v1/notificaciones/dispositivos/tok-abc").with(usuario(500, "APODERADO"))).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/notificaciones/dispositivos/tok-abc").with(usuario(600, "APODERADO"))).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select activo from dispositivos_push", Boolean.class)).isFalse();
        enviar("POST", "/api/v1/notificaciones/dispositivos", "{\"token\":\"\",\"plataforma\":\"WEB\"}", usuario(600, "APODERADO"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void plantillasYEnvioManualSonSoloDelAdmin() throws Exception {
        mvc.perform(get("/api/v1/notificaciones/plantillas").with(usuario(500, "APODERADO"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/notificaciones/plantillas").with(usuario(2, "SECRETARIA"))).andExpect(status().isForbidden());
        // el 403 llega antes que el 400 aunque el cuerpo sea inválido
        enviar("POST", "/api/v1/notificaciones/enviar", "{}", usuario(500, "APODERADO")).andExpect(status().isForbidden());

        String lista = mvc.perform(get("/api/v1/notificaciones/plantillas").with(usuario(1, "ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(20))).andReturn().getResponse().getContentAsString();
        Number id = ((List<Number>) JsonPath.read(lista, "$[?(@.codigo=='PAGO_CONFIRMADO' && @.canal=='IN_APP')].id")).get(0);
        enviar("PUT", "/api/v1/notificaciones/plantillas/" + id, "{\"asunto\":\"Pago OK\",\"cuerpo\":\"Pagaste S/ {{monto}}\",\"activa\":true}", usuario(1, "ADMIN"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cuerpo").value("Pagaste S/ {{monto}}"));
        enviar("PUT", "/api/v1/notificaciones/plantillas/" + id, "{\"cuerpo\":\"\"}", usuario(1, "ADMIN")).andExpect(status().isBadRequest());
        enviar("PUT", "/api/v1/notificaciones/plantillas/999999", "{\"cuerpo\":\"x\"}", usuario(1, "ADMIN")).andExpect(status().isNotFound());
        jdbc.update("update plantillas_notificacion set asunto = 'Pago confirmado', cuerpo = 'Recibimos tu pago de S/ {{monto}} por {{concepto}}.' where id = ?", id.longValue());
    }

    @Test
    void envioManualCreaAvisosPorBandejaYPushYOmiteLoQueNoAplica() throws Exception {
        enviar("POST", "/api/v1/notificaciones/dispositivos", "{\"token\":\"tok-800\",\"plataforma\":\"ANDROID\"}", usuario(800, "DOCENTE")).andExpect(status().isCreated());
        var admin = usuario(1, "ADMIN");

        enviar("POST", "/api/v1/notificaciones/enviar",
                "{\"usuarioIds\":[800,801],\"titulo\":\"Reunión\",\"mensaje\":\"Mañana a las 8:00\",\"canales\":[\"IN_APP\",\"PUSH\"]}", admin)
                .andExpect(status().isOk()).andExpect(jsonPath("$.creadas").value(3)).andExpect(jsonPath("$.omitidas").value(1));   // 801 no tiene dispositivo

        mvc.perform(get("/api/v1/notificaciones/mias").with(usuario(800, "DOCENTE")))
                .andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.contenido[0].titulo").value("Reunión"));
        mvc.perform(get("/api/v1/notificaciones/mias").with(usuario(801, "DOCENTE"))).andExpect(jsonPath("$.total").value(1));
        verify(pushSender).enviar(eq(List.of("tok-800")), eq("Reunión"), eq("Mañana a las 8:00"));

        enviar("POST", "/api/v1/notificaciones/enviar", "{\"usuarioIds\":[800],\"titulo\":\"x\",\"mensaje\":\"y\",\"canales\":[\"EMAIL\"]}", admin)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.mensaje", containsString("correo")));
        enviar("POST", "/api/v1/notificaciones/enviar", "{\"usuarioIds\":[],\"titulo\":\"x\",\"mensaje\":\"y\",\"canales\":[\"IN_APP\"]}", admin)
                .andExpect(status().isBadRequest());
    }

    @Test
    void rutaInexistenteEs404YMetodoNoPermitidoEs405_noEs500() throws Exception {
        mvc.perform(get("/api/v1/notificaciones/no-existe").with(usuario(500, "APODERADO"))).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/notificaciones/mias").with(usuario(500, "APODERADO"))).andExpect(status().isMethodNotAllowed());
        mvc.perform(get("/api/v1/notificaciones/abc/leida").with(usuario(500, "APODERADO"))).andExpect(status().isMethodNotAllowed());
    }

    @Test
    void shedLockDejaPasarAUnaSolaReplica() {
        var config = new LockConfiguration(Instant.now(), "prueba-shedlock", Duration.ofMinutes(1), Duration.ZERO);
        var primera = lockProvider.lock(config);
        var segunda = lockProvider.lock(config);
        assertThat(primera).isPresent();
        assertThat(segunda).as("una segunda réplica no obtiene el candado").isEmpty();
        primera.get().unlock();
        assertThat(lockProvider.lock(config)).isPresent();
    }
}
