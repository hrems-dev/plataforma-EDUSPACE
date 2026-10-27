package pe.edu.upeu.eduspace.tareas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import io.minio.MinioClient;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pe.edu.upeu.eduspace.tareas.client.*;
import pe.edu.upeu.eduspace.tareas.config.KafkaConfig;
import pe.edu.upeu.eduspace.tareas.dto.Dtos.*;
import pe.edu.upeu.eduspace.tareas.event.EventoSobre;
import pe.edu.upeu.eduspace.tareas.event.MatriculaEvent;
import pe.edu.upeu.eduspace.tareas.exception.BusinessRuleException;
import pe.edu.upeu.eduspace.tareas.scheduler.CierreAutomaticoJob;
import pe.edu.upeu.eduspace.tareas.scheduler.RecordatorioJob;
import pe.edu.upeu.eduspace.tareas.service.*;

@AutoConfigureMockMvc
class TareasIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired TareaService tareas;
    @Autowired EntregaService entregas;
    @Autowired CalificacionService calificaciones;
    @Autowired MatriculaLocalService matriculaLocal;
    @Autowired RecordatorioJob recordatorio;
    @Autowired CierreAutomaticoJob cierreJob;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;

    @MockitoBean PlanificacionGateway planificacion;
    @MockitoBean AlumnosGateway alumnos;
    @MockitoBean MatriculaClient matriculaClient;
    @MockitoBean MinioClient minio;
    @MockitoBean KafkaTemplate<String, String> kafka;

    static final long SECCION = 10, SECCION_B = 11, ANIO = 1, CURSO = 7, PERIODO = 3, DOCENTE = 50, U_DOCENTE = 5;

    @BeforeEach
    void preparar() throws Exception {
        jdbc.execute("TRUNCATE entrega_archivos, entregas, tarea_adjuntos, tareas, matriculas_local, eventos_procesados RESTART IDENTITY CASCADE");
        reset(planificacion, alumnos, matriculaClient, minio, kafka);
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));
        when(minio.getPresignedObjectUrl(any())).thenReturn("http://minio/firmada");
        when(planificacion.docentePorUsuario(U_DOCENTE)).thenReturn(new PlanificacionClient.Docente(DOCENTE, "Prof. Díaz", "ACTIVO"));
        when(planificacion.asignacionValida(DOCENTE, SECCION, CURSO)).thenReturn(true);
        when(planificacion.seccion(SECCION)).thenReturn(new PlanificacionClient.Seccion(SECCION, "A", 3L, 1L, ANIO, (short) 30, "MAÑANA", null));
        when(planificacion.periodo(PERIODO)).thenReturn(new PlanificacionClient.Periodo(PERIODO, ANIO, (short) 2, "II Bimestre",
            java.time.LocalDate.now().minusDays(30), java.time.LocalDate.now().plusDays(30), "ACTIVO"));
        when(planificacion.nombreCursoOpcional(CURSO)).thenReturn("Matemática");
        for (long m = 1; m <= 4; m++) matricula(m, SECCION);
        matricula(9, SECCION_B);
    }

    void matricula(long id, long seccion) {
        MatriculaEvent ev = new MatriculaEvent();
        ev.setMatriculaId(id); ev.setAlumnoId(id); ev.setAlumnoNombre("Alumno " + id);
        ev.setAnioId(ANIO); ev.setGradoId(3L); ev.setSeccionId(seccion); ev.setEstado("MATRICULADO");
        matriculaLocal.procesar(EventoSobre.<MatriculaEvent>builder().eventId(UUID.randomUUID()).eventType("matricula.confirmada")
            .occurredAt(LocalDateTime.now()).payload(ev).build());
    }

    TareaResponse nueva(LocalDateTime entrega, boolean tardia, String puntaje) {
        return tareas.crear(new TareaCreateRequest(PERIODO, SECCION, CURSO, "Ejercicios de fracciones", "Resolver la guía",
            entrega, tardia, puntaje == null ? null : new BigDecimal(puntaje)), U_DOCENTE, false);
    }

    TareaResponse publicada(boolean tardia, String puntaje) {
        TareaResponse t = nueva(LocalDateTime.now().plusDays(2), tardia, puntaje);
        return tareas.publicar(t.id(), U_DOCENTE, false);
    }

    EntregaResponse entregar(long tareaId, long alumnoId) {
        return entregas.registrar(tareaId, new EntregaRequest("Listo profe", List.of(new ArchivoRef("tarea.pdf",
            "tareas/" + tareaId + "/entregas/" + alumnoId + "/abc-tarea.pdf", "application/pdf", 1000L))), alumnoId);
    }

    // ------------------------------------------------------------------ gestión

    @Test
    void docenteAsignadoCreaBorradorConPuntajePorDefecto20() {
        TareaResponse t = nueva(LocalDateTime.now().plusDays(3), false, null);
        assertThat(t.estado()).isEqualTo("BORRADOR");
        assertThat(t.puntajeMaximo()).isEqualByComparingTo("20");
        assertThat(t.docenteId()).isEqualTo(DOCENTE);
        assertThat(t.anioId()).isEqualTo(ANIO);
    }

    @Test
    void docenteNoAsignadoNoCreaTareas() {
        when(planificacion.docentePorUsuario(6L)).thenReturn(new PlanificacionClient.Docente(60L, "Otro", "ACTIVO"));
        assertThatThrownBy(() -> tareas.crear(new TareaCreateRequest(PERIODO, SECCION, CURSO, "X", null,
            LocalDateTime.now().plusDays(1), false, null), 6L, false)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void periodoInactivoOAjenoAlAnioSeRechaza() {
        when(planificacion.periodo(PERIODO)).thenReturn(new PlanificacionClient.Periodo(PERIODO, ANIO, (short) 1, "I Bimestre", null, null, "CERRADO"));
        assertThatThrownBy(() -> nueva(LocalDateTime.now().plusDays(2), false, null)).isInstanceOf(BusinessRuleException.class).hasMessageContaining("ACTIVO");
        when(planificacion.periodo(PERIODO)).thenReturn(new PlanificacionClient.Periodo(PERIODO, 99L, (short) 1, "Otro año", null, null, "ACTIVO"));
        assertThatThrownBy(() -> nueva(LocalDateTime.now().plusDays(2), false, null)).isInstanceOf(BusinessRuleException.class).hasMessageContaining("año");
    }

    @Test
    void publicarExigeFechaFuturaYEmiteEventoConLosAlumnosDeLaSeccion() {
        TareaResponse vencida = nueva(LocalDateTime.now().minusHours(1), false, null);
        assertThatThrownBy(() -> tareas.publicar(vencida.id(), U_DOCENTE, false)).isInstanceOf(BusinessRuleException.class).hasMessageContaining("futura");

        TareaResponse p = publicada(false, null);
        assertThat(p.estado()).isEqualTo("PUBLICADA");
        var json = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(kafka).send(eq(KafkaConfig.TOPIC_TAREA_PUBLICADA), eq(String.valueOf(p.id())), json.capture());
        assertThat(json.getValue()).contains("\"eventType\":\"tarea.publicada\"").contains("\"cursoNombre\":\"Matemática\"")
            .contains("\"alumnoIds\":[1,2,3,4]").doesNotContain("\"payload\"");
        assertThatThrownBy(() -> tareas.publicar(p.id(), U_DOCENTE, false)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void tareaPublicadaSoloEditaDescripcionYFecha() {
        TareaResponse p = publicada(false, "10");
        TareaResponse e = tareas.actualizar(p.id(), new TareaUpdateRequest(null, "Nueva descripción", LocalDateTime.now().plusDays(5), null, null), U_DOCENTE, false);
        assertThat(e.descripcion()).isEqualTo("Nueva descripción");
        assertThatThrownBy(() -> tareas.actualizar(p.id(), new TareaUpdateRequest(null, null, null, null, new BigDecimal("15")), U_DOCENTE, false))
            .isInstanceOf(BusinessRuleException.class).hasMessageContaining("descripción y la fecha");
    }

    @Test
    void otroDocenteNoTocaLaTareaAjena() {
        TareaResponse p = publicada(false, null);
        when(planificacion.docentePorUsuario(6L)).thenReturn(new PlanificacionClient.Docente(60L, "Otro", "ACTIVO"));
        assertThatThrownBy(() -> tareas.cerrar(p.id(), 6L, false)).isInstanceOf(AccessDeniedException.class);
    }

    // ------------------------------------------------------------------ entregas

    @Test
    void alumnoDeOtraSeccionNoPuedeEntregar() {
        TareaResponse p = publicada(false, null);
        assertThatThrownBy(() -> entregar(p.id(), 9)).isInstanceOf(BusinessRuleException.class).hasMessageContaining("no está matriculado");
    }

    @Test
    void entregaRegistraVersionEvento_yReenvioIncrementaLaVersion() {
        TareaResponse p = publicada(false, null);
        EntregaResponse e1 = entregar(p.id(), 1);
        assertThat(e1.version()).isEqualTo((short) 1);
        assertThat(e1.esTardia()).isFalse();
        assertThat(e1.archivos()).hasSize(1);
        EntregaResponse e2 = entregas.registrar(p.id(), new EntregaRequest("Versión corregida", List.of()), 1L);
        assertThat(e2.version()).isEqualTo((short) 2);
        assertThat(e2.archivos()).isEmpty();
        assertThat(e2.id()).isEqualTo(e1.id());
        assertThat(jdbc.queryForObject("select count(*) from entregas", Integer.class)).isEqualTo(1);
        verify(kafka, times(2)).send(eq(KafkaConfig.TOPIC_TAREA_ENTREGADA), eq(String.valueOf(e1.id())), anyString());
    }

    @Test
    void entregaVacia_oConArchivoAjeno_oTipoInvalido_seRechaza() {
        TareaResponse p = publicada(false, null);
        assertThatThrownBy(() -> entregas.registrar(p.id(), new EntregaRequest(null, List.of()), 1L)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> entregas.registrar(p.id(), new EntregaRequest("x", List.of(new ArchivoRef("a.pdf",
            "tareas/" + p.id() + "/entregas/2/abc-a.pdf", "application/pdf", 10L))), 1L))
            .isInstanceOf(BusinessRuleException.class).hasMessageContaining("URL firmada");
        assertThatThrownBy(() -> entregas.registrar(p.id(), new EntregaRequest("x", List.of(new ArchivoRef("a.exe",
            "tareas/" + p.id() + "/entregas/1/abc-a.exe", "application/x-msdownload", 10L))), 1L))
            .isInstanceOf(BusinessRuleException.class).hasMessageContaining("no permitido");
    }

    @Test
    void urlFirmadaRechazaTiposYTamaniosNoPermitidos() {
        TareaResponse p = publicada(false, null);
        assertThatThrownBy(() -> entregas.urlSubida(p.id(), new ArchivoUrlRequest("virus.exe", "application/x-msdownload", 100L), 1L))
            .isInstanceOf(BusinessRuleException.class).hasMessageContaining("no permitido");
        assertThatThrownBy(() -> entregas.urlSubida(p.id(), new ArchivoUrlRequest("enorme.pdf", "application/pdf", 50L * 1024 * 1024), 1L))
            .isInstanceOf(BusinessRuleException.class).hasMessageContaining("tamaño");
        ArchivoUrlResponse ok = entregas.urlSubida(p.id(), new ArchivoUrlRequest("mi tarea (1).pdf", "application/pdf", 2048L), 1L);
        assertThat(ok.ruta()).startsWith("tareas/" + p.id() + "/entregas/1/").endsWith("mi_tarea__1_.pdf");
        assertThat(ok.uploadUrl()).isEqualTo("http://minio/firmada");
    }

    @Test
    void fueraDePlazoSoloEntregaSiLaTareaPermiteTardia() {
        TareaResponse sinTardia = publicada(false, null);
        TareaResponse conTardia = publicada(true, null);
        jdbc.update("update tareas set fecha_entrega = now() - interval '2 hours'");
        assertThatThrownBy(() -> entregar(sinTardia.id(), 1)).isInstanceOf(BusinessRuleException.class).hasMessageContaining("venció");
        EntregaResponse tarde = entregar(conTardia.id(), 1);
        assertThat(tarde.esTardia()).isTrue();
    }

    @Test
    void despuesDeCalificarYaNoSePuedeReenviar() {
        TareaResponse p = publicada(false, null);
        EntregaResponse e = entregar(p.id(), 1);
        calificaciones.calificar(e.id(), new CalificarRequest(new BigDecimal("16"), "Bien"), U_DOCENTE, false);
        assertThatThrownBy(() -> entregar(p.id(), 1)).isInstanceOf(BusinessRuleException.class).hasMessageContaining("calificada");
    }

    // ------------------------------------------------------------------ calificación

    @Test
    void calificarConvierteLaNotaAEscala20YPublicaElEventoConPeriodoYCurso() {
        TareaResponse p = publicada(false, "10");               // puntaje máximo 10
        EntregaResponse e = entregar(p.id(), 2);
        EntregaResponse c = calificaciones.calificar(e.id(), new CalificarRequest(new BigDecimal("7"), "Muy bien"), U_DOCENTE, false);
        assertThat(c.estado()).isEqualTo("CALIFICADA");
        assertThat(c.nota()).isEqualByComparingTo("7");

        var json = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(kafka).send(eq(KafkaConfig.TOPIC_TAREA_CALIFICADA), eq(String.valueOf(e.id())), json.capture());
        assertThat(json.getValue()).contains("\"nota\":14.00").contains("\"periodoId\":3").contains("\"cursoId\":7")
            .contains("\"matriculaId\":2").contains("\"seccionId\":10").contains("\"alumnoId\":2").contains("\"titulo\":\"Ejercicios de fracciones\"")
            .doesNotContain("\"payload\"");
    }

    @Test
    void recalificarVuelveAPublicarConLaNuevaNota() {
        TareaResponse p = publicada(false, null);
        EntregaResponse e = entregar(p.id(), 1);
        calificaciones.calificar(e.id(), new CalificarRequest(new BigDecimal("12"), null), U_DOCENTE, false);
        calificaciones.calificar(e.id(), new CalificarRequest(new BigDecimal("18"), "Corregida"), U_DOCENTE, false);
        verify(kafka, times(2)).send(eq(KafkaConfig.TOPIC_TAREA_CALIFICADA), eq(String.valueOf(e.id())), anyString());
        assertThat(jdbc.queryForObject("select nota from entregas", BigDecimal.class)).isEqualByComparingTo("18");
    }

    @Test
    void notaMayorAlPuntajeMaximoSeRechaza() {
        TareaResponse p = publicada(false, null);
        EntregaResponse e = entregar(p.id(), 1);
        assertThatThrownBy(() -> calificaciones.calificar(e.id(), new CalificarRequest(new BigDecimal("21"), null), U_DOCENTE, false))
            .isInstanceOf(BusinessRuleException.class).hasMessageContaining("puntaje máximo");
    }

    @Test
    void devolverPermiteReenviarYNoPublicaNota() {
        TareaResponse p = publicada(false, null);
        EntregaResponse e = entregar(p.id(), 1);
        EntregaResponse d = calificaciones.devolver(e.id(), new DevolverRequest("Falta el desarrollo del ejercicio 3"), U_DOCENTE, false);
        assertThat(d.estado()).isEqualTo("DEVUELTA");
        verify(kafka, never()).send(eq(KafkaConfig.TOPIC_TAREA_CALIFICADA), anyString(), anyString());
        EntregaResponse r = entregar(p.id(), 1);
        assertThat(r.estado()).isEqualTo("ENTREGADA");
        assertThat(r.version()).isEqualTo((short) 2);
    }

    @Test
    void calificacionMasivaReportaErrores() {
        TareaResponse p = publicada(false, null);
        EntregaResponse e1 = entregar(p.id(), 1);
        EntregaResponse e2 = entregar(p.id(), 2);
        CalificacionMasivaResultado r = calificaciones.calificarMasivo(p.id(), new CalificacionMasivaRequest(List.of(
            new NotaItem(e1.id(), new BigDecimal("15"), null),
            new NotaItem(e2.id(), new BigDecimal("25"), null),
            new NotaItem(999L, new BigDecimal("10"), null))), U_DOCENTE, false);
        assertThat(r.calificadas()).isEqualTo(1);
        assertThat(r.errores()).hasSize(2);
    }

    @Test
    void entregasDeLaTareaIncluyeALosQueNoEntregaron() {
        TareaResponse p = publicada(false, null);
        entregar(p.id(), 1);
        List<EntregaAlumno> lista = entregas.deLaTarea(p.id(), U_DOCENTE, false);
        assertThat(lista).hasSize(4);
        assertThat(lista.stream().filter(x -> x.estado().equals("SIN_ENTREGAR"))).hasSize(3);
        TareaResponse detalle = tareas.detalle(p.id());
        assertThat(detalle.entregadas()).isEqualTo(1);
        assertThat(detalle.pendientes()).isEqualTo(3);
    }

    // ------------------------------------------------------------------ jobs

    @Test
    void recordatorioSeEnviaUnaSolaVezSoloAQuienesNoEntregaron() {
        TareaResponse p = publicada(false, null);
        entregar(p.id(), 1);
        jdbc.update("update tareas set fecha_entrega = now() + interval '10 hours'");
        assertThat(recordatorio.ejecutar()).isEqualTo(1);
        var json = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(kafka).send(eq(KafkaConfig.TOPIC_TAREA_RECORDATORIO), eq(String.valueOf(p.id())), json.capture());
        assertThat(json.getValue()).contains("\"alumnoIds\":[2,3,4]");
        assertThat(recordatorio.ejecutar()).isZero();                       // no se duplica
    }

    @Test
    void tareasVencidasSinTardiaSeCierranSolas() {
        TareaResponse sin = publicada(false, null);
        TareaResponse con = publicada(true, null);
        jdbc.update("update tareas set fecha_entrega = now() - interval '1 hour'");
        assertThat(cierreJob.ejecutar()).isEqualTo(1);
        assertThat(tareas.detalle(sin.id()).estado()).isEqualTo("CERRADA");
        assertThat(tareas.detalle(con.id()).estado()).isEqualTo("PUBLICADA");
    }

    // ------------------------------------------------------------------ API y seguridad

    JwtRequestPostProcessor usuario(long id, String rol) {
        return jwt().jwt(j -> j.claim("usuarioId", id).claim("realm_access", Map.of("roles", List.of(rol))))
            .authorities(new SimpleGrantedAuthority("ROLE_" + rol));
    }

    @Test
    void rolesYPropiedadEnLaApi() throws Exception {
        TareaResponse p = publicada(false, null);
        when(alumnos.alumnoIdDeUsuario(301L)).thenReturn(1L);
        when(alumnos.alumnoIdDeUsuario(309L)).thenReturn(9L);
        when(alumnos.hijosDeUsuario(700L)).thenReturn(List.of(new AlumnosClient.Hijo(1L, "A-1", "Alumno 1", "ACTIVO", "PADRE")));
        String crear = "{\"periodoId\":3,\"seccionId\":10,\"cursoId\":7,\"titulo\":\"T\",\"fechaEntrega\":\"%s\"}".formatted(LocalDateTime.now().plusDays(3));

        // el alumno no crea tareas; el docente sí
        mvc.perform(post("/api/v1/tareas").with(usuario(301, "ALUMNO")).contentType(MediaType.APPLICATION_JSON).content(crear)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/tareas").with(usuario(U_DOCENTE, "DOCENTE")).contentType(MediaType.APPLICATION_JSON).content(crear)).andExpect(status().isCreated());

        // el apoderado es solo lectura: no entrega
        mvc.perform(post("/api/v1/tareas/" + p.id() + "/entregas").with(usuario(700, "APODERADO")).contentType(MediaType.APPLICATION_JSON)
            .content("{\"comentario\":\"hola\"}")).andExpect(status().isForbidden());
        // el alumno de la sección entrega; el de otra sección no
        mvc.perform(post("/api/v1/tareas/" + p.id() + "/entregas").with(usuario(301, "ALUMNO")).contentType(MediaType.APPLICATION_JSON)
            .content("{\"comentario\":\"hola\"}")).andExpect(status().isCreated()).andExpect(jsonPath("$.version").value(1));
        mvc.perform(post("/api/v1/tareas/" + p.id() + "/entregas").with(usuario(309, "ALUMNO")).contentType(MediaType.APPLICATION_JSON)
            .content("{\"comentario\":\"hola\"}")).andExpect(status().isConflict());

        // detalle: el participante lo ve, el ajeno no
        mvc.perform(get("/api/v1/tareas/" + p.id()).with(usuario(700, "APODERADO"))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/tareas/" + p.id()).with(usuario(309, "ALUMNO"))).andExpect(status().isForbidden());

        // tareas del hijo y pendientes
        mvc.perform(get("/api/v1/tareas/mias").with(usuario(700, "APODERADO"))).andExpect(status().isOk())
            .andExpect(jsonPath("$[0].estadoEntrega").value("ENTREGADA"));
        mvc.perform(get("/api/v1/tareas/mias/pendientes").with(usuario(700, "APODERADO"))).andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));

        // el alumno no ve las entregas de la tarea; calificar es del docente
        mvc.perform(get("/api/v1/tareas/" + p.id() + "/entregas").with(usuario(301, "ALUMNO"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/tareas/" + p.id() + "/entregas").with(usuario(U_DOCENTE, "DOCENTE"))).andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(4));
        Long entregaId = jdbc.queryForObject("select id from entregas", Long.class);
        mvc.perform(patch("/api/v1/tareas/entregas/" + entregaId + "/calificar").with(usuario(301, "ALUMNO"))
            .contentType(MediaType.APPLICATION_JSON).content("{\"nota\":20}")).andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/tareas/entregas/" + entregaId + "/calificar").with(usuario(U_DOCENTE, "DOCENTE"))
            .contentType(MediaType.APPLICATION_JSON).content("{\"nota\":17.5,\"retroalimentacion\":\"Bien\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("CALIFICADA"));
        mvc.perform(patch("/api/v1/tareas/entregas/" + entregaId + "/calificar").with(usuario(U_DOCENTE, "DOCENTE"))
            .contentType(MediaType.APPLICATION_JSON).content("{\"nota\":-1}")).andExpect(status().isBadRequest());
    }
}
