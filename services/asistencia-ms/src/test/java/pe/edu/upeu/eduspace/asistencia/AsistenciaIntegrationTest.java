package pe.edu.upeu.eduspace.asistencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDate;
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
import pe.edu.upeu.eduspace.asistencia.client.*;
import pe.edu.upeu.eduspace.asistencia.config.KafkaConfig;
import pe.edu.upeu.eduspace.asistencia.dto.Dtos.*;
import pe.edu.upeu.eduspace.asistencia.event.EventoSobre;
import pe.edu.upeu.eduspace.asistencia.event.MatriculaEvent;
import pe.edu.upeu.eduspace.asistencia.exception.BusinessRuleException;
import pe.edu.upeu.eduspace.asistencia.exception.ServicioNoDisponibleException;
import pe.edu.upeu.eduspace.asistencia.service.*;

@AutoConfigureMockMvc
class AsistenciaIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired SesionService sesiones;
    @Autowired RegistroAsistenciaService registro;
    @Autowired CierreSesionService cierre;
    @Autowired CorreccionService correccion;
    @Autowired JustificacionService justificaciones;
    @Autowired ReporteService reportes;
    @Autowired MatriculaLocalService matriculaLocal;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;

    @MockitoBean PlanificacionGateway planificacion;
    @MockitoBean AlumnosGateway alumnos;
    @MockitoBean ArchivoService archivos;
    @MockitoBean MatriculaClient matriculaClient;
    @MockitoBean KafkaTemplate<String, String> kafka;

    static final long SECCION = 10, SECCION_B = 11, ANIO = 1, DOCENTE = 50, USUARIO_DOCENTE = 5;
    final LocalDate hoy = LocalDate.now();

    @BeforeEach
    void preparar() {
        jdbc.execute("TRUNCATE asistencia_auditoria, justificaciones, asistencia_detalle, sesiones_asistencia, matriculas_local, eventos_procesados RESTART IDENTITY CASCADE");
        reset(planificacion, alumnos, archivos, matriculaClient, kafka);
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));
        when(planificacion.esHabil(any())).thenReturn(true);
        when(planificacion.docentePorUsuario(USUARIO_DOCENTE)).thenReturn(new PlanificacionClient.Docente(DOCENTE, "Prof. Díaz", "ACTIVO"));
        when(planificacion.asignacionValida(eq(DOCENTE), eq(SECCION), anyLong())).thenReturn(true);
        when(planificacion.asignaciones(eq(USUARIO_DOCENTE), anyLong())).thenReturn(List.of(new PlanificacionClient.Asignacion(SECCION, 7L, 1L)));
        seccion(SECCION);
        seccion(SECCION_B);
        when(planificacion.periodoDeFecha(anyLong(), any())).thenReturn(3L);
        for (long m = 1; m <= 4; m++) matricula(m, m, SECCION, "PRE_MATRICULA", "matricula.creada");
        for (long m = 1; m <= 4; m++) matricula(m, m, SECCION, "MATRICULADO", "matricula.confirmada");
    }

    void seccion(long id) {
        when(planificacion.seccion(id)).thenReturn(new PlanificacionClient.Seccion(id, "S" + id, 3L, 1L, ANIO, (short) 30, "MAÑANA", null));
    }

    void matricula(long matriculaId, long alumnoId, long seccionId, String estado, String topic) {
        MatriculaEvent ev = new MatriculaEvent();
        ev.setMatriculaId(matriculaId); ev.setAlumnoId(alumnoId); ev.setAlumnoNombre("Alumno " + alumnoId);
        ev.setAnioId(ANIO); ev.setGradoId(3L); ev.setSeccionId(seccionId); ev.setEstado(estado);
        matriculaLocal.procesar(EventoSobre.<MatriculaEvent>builder().eventId(UUID.randomUUID()).eventType(topic)
            .occurredAt(LocalDateTime.now().plusSeconds(matriculaId * 10 + (estado.equals("MATRICULADO") ? 5 : 0))).payload(ev).build());
    }

    SesionResponse abrirDia(LocalDate fecha) {
        return sesiones.abrir(new SesionCreateRequest(SECCION, fecha, "DIA", null, null, null), USUARIO_DOCENTE, false);
    }

    RegistroAsistenciaRequest lote(String... estados) {
        List<ItemRegistro> items = new java.util.ArrayList<>();
        for (int i = 0; i < estados.length; i++) {
            items.add(new ItemRegistro((long) (i + 1), estados[i], "TARDANZA".equals(estados[i]) ? (short) 10 : null, null));
        }
        return new RegistroAsistenciaRequest(items);
    }

    int faltasPublicadas() {
        var c = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(kafka, atLeast(0)).send(eq(KafkaConfig.TOPIC_FALTA_REGISTRADA), anyString(), c.capture());
        return c.getAllValues().size();
    }

    // ------------------------------------------------------------------ apertura

    @Test
    void abrirPrecargaATodosLosAlumnosMatriculadosComoPresentes() {
        SesionResponse s = abrirDia(hoy);
        assertThat(s.alumnos()).hasSize(4).allMatch(a -> a.estado().equals("PRESENTE"));
        assertThat(s.periodoId()).isEqualTo(3L);
        assertThat(s.docenteId()).isEqualTo(DOCENTE);
        assertThat(s.estado()).isEqualTo("ABIERTA");
    }

    @Test
    void abrirDosVecesDevuelveLaMismaHoja() {
        assertThat(abrirDia(hoy).id()).isEqualTo(abrirDia(hoy).id());
        assertThat(jdbc.queryForObject("select count(*) from sesiones_asistencia", Integer.class)).isEqualTo(1);
    }

    @Test
    void docenteNoAsignadoRecibe403() {
        when(planificacion.docentePorUsuario(6L)).thenReturn(new PlanificacionClient.Docente(60L, "Otro", "ACTIVO"));
        when(planificacion.asignaciones(eq(6L), anyLong())).thenReturn(List.of());
        assertThatThrownBy(() -> sesiones.abrir(new SesionCreateRequest(SECCION, hoy, "DIA", null, null, null), 6L, false))
            .isInstanceOf(AccessDeniedException.class);
        when(planificacion.asignacionValida(60L, SECCION, 7L)).thenReturn(false);
        assertThatThrownBy(() -> sesiones.abrir(new SesionCreateRequest(SECCION, hoy, "CURSO", 7L, 2L, null), 6L, false))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void fechaFeriadoOFutura409() {
        when(planificacion.esHabil(hoy.minusDays(1))).thenReturn(false);
        assertThatThrownBy(() -> abrirDia(hoy.minusDays(1))).isInstanceOf(BusinessRuleException.class).hasMessageContaining("hábil");
        assertThatThrownBy(() -> abrirDia(hoy.plusDays(1))).isInstanceOf(BusinessRuleException.class).hasMessageContaining("futura");
    }

    @Test
    void sesionCursoRequiereCursoYBloque() {
        assertThatThrownBy(() -> sesiones.abrir(new SesionCreateRequest(SECCION, hoy, "CURSO", 7L, null, null), USUARIO_DOCENTE, false))
            .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> sesiones.abrir(new SesionCreateRequest(SECCION, hoy, "DIA", 7L, null, null), USUARIO_DOCENTE, false))
            .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void sesionPorCursoConDocenteAsignado() {
        SesionResponse s = sesiones.abrir(new SesionCreateRequest(SECCION, hoy, "CURSO", 7L, 2L, null), USUARIO_DOCENTE, false);
        assertThat(s.tipo()).isEqualTo("CURSO");
        // el día y el curso conviven en la misma fecha
        assertThat(abrirDia(hoy).id()).isNotEqualTo(s.id());
    }

    // ------------------------------------------------------------------ registro

    @Test
    void guardarDosVecesLaMismaHojaNoDuplicaFaltasNiNotificaciones() {
        SesionResponse s = abrirDia(hoy);
        registro.registrar(s.id(), lote("FALTA", "PRESENTE", "PRESENTE", "FALTA"), USUARIO_DOCENTE, false);
        registro.registrar(s.id(), lote("FALTA", "PRESENTE", "PRESENTE", "FALTA"), USUARIO_DOCENTE, false);
        assertThat(jdbc.queryForObject("select count(*) from asistencia_detalle where estado = 'FALTA'", Integer.class)).isEqualTo(2);
        assertThat(faltasPublicadas()).isEqualTo(2);
        verify(kafka, times(2)).send(eq(KafkaConfig.TOPIC_FALTA_REGISTRADA), anyString(), contains("\"eventType\":\"asistencia.falta-registrada\""));
    }

    @Test
    void tardanzaExigeMinutosYLaMatriculaDebeSerDeLaSeccion() {
        SesionResponse s = abrirDia(hoy);
        assertThatThrownBy(() -> registro.registrar(s.id(),
                new RegistroAsistenciaRequest(List.of(new ItemRegistro(1L, "TARDANZA", null, null))), USUARIO_DOCENTE, false))
            .isInstanceOf(BusinessRuleException.class).hasMessageContaining("minutos");
        matricula(99, 99, SECCION_B, "MATRICULADO", "matricula.confirmada");
        assertThatThrownBy(() -> registro.registrar(s.id(),
                new RegistroAsistenciaRequest(List.of(new ItemRegistro(99L, "PRESENTE", null, null))), USUARIO_DOCENTE, false))
            .isInstanceOf(BusinessRuleException.class).hasMessageContaining("no pertenece");
    }

    @Test
    void otroDocenteNoPuedeRegistrarEnLaSesionAjena() {
        SesionResponse s = abrirDia(hoy);
        when(planificacion.docentePorUsuario(6L)).thenReturn(new PlanificacionClient.Docente(60L, "Otro", "ACTIVO"));
        assertThatThrownBy(() -> registro.registrar(s.id(), lote("PRESENTE"), 6L, false)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void sesionCerradaRechazaRegistroYSoloSecretariaCorrigeConAuditoria() {
        SesionResponse s = abrirDia(hoy);
        registro.registrar(s.id(), lote("PRESENTE", "PRESENTE", "PRESENTE", "PRESENTE"), USUARIO_DOCENTE, false);
        cierre.cerrar(s.id(), USUARIO_DOCENTE, false);
        assertThatThrownBy(() -> registro.registrar(s.id(), lote("FALTA"), USUARIO_DOCENTE, false))
            .isInstanceOf(BusinessRuleException.class).hasMessageContaining("CERRADA");

        long detalleId = sesiones.hoja(s.id()).alumnos().get(0).detalleId();
        DetalleResponse corregido = correccion.corregir(detalleId, new CorreccionRequest("FALTA", null, "Error del docente al pasar lista"), 77L);
        assertThat(corregido.estado()).isEqualTo("FALTA");
        assertThat(jdbc.queryForMap("select estado_anterior, estado_nuevo, motivo, usuario_id from asistencia_auditoria"))
            .containsEntry("estado_anterior", "PRESENTE").containsEntry("estado_nuevo", "FALTA").containsEntry("usuario_id", 77L);
        assertThat(faltasPublicadas()).isEqualTo(1);
    }

    @Test
    void cierreNocturnoCierraLasSesionesDeDiasAnteriores() {
        abrirDia(hoy.minusDays(2));
        abrirDia(hoy.minusDays(1));
        SesionResponse deHoy = abrirDia(hoy);
        assertThat(cierre.cerrarVencidas()).isEqualTo(2);
        assertThat(sesiones.hoja(deHoy.id()).estado()).isEqualTo("ABIERTA");
        assertThat(jdbc.queryForObject("select count(*) from sesiones_asistencia where estado = 'CERRADA'", Integer.class)).isEqualTo(2);
    }

    // ------------------------------------------------------------------ eventos de matrícula

    @Test
    void trasladoActualizaLaListaDelDiaSiguienteNoLaHistoria() {
        SesionResponse ayer = abrirDia(hoy.minusDays(1));
        assertThat(ayer.alumnos()).hasSize(4);
        // matrícula 1 se traslada a la sección B
        MatriculaEvent ev = new MatriculaEvent();
        ev.setMatriculaId(1L); ev.setAlumnoId(1L); ev.setAlumnoNombre("Alumno 1"); ev.setSeccionId(SECCION_B); ev.setEstado("MATRICULADO");
        matriculaLocal.procesar(EventoSobre.<MatriculaEvent>builder().eventId(UUID.randomUUID()).eventType("matricula.trasladada")
            .occurredAt(LocalDateTime.now().plusHours(1)).payload(ev).build());

        assertThat(sesiones.hoja(ayer.id()).alumnos()).hasSize(4);                         // la historia no cambia
        assertThat(abrirDia(hoy).alumnos()).hasSize(3).noneMatch(a -> a.alumnoId() == 1L);  // la lista nueva sí
    }

    @Test
    void eventoDuplicadoOViejoNoModificaLaReplica() {
        UUID id = UUID.randomUUID();
        MatriculaEvent ev = new MatriculaEvent();
        ev.setMatriculaId(2L); ev.setAlumnoId(2L); ev.setAlumnoNombre("Alumno 2"); ev.setSeccionId(SECCION); ev.setEstado("RETIRADO");
        var sobre = EventoSobre.<MatriculaEvent>builder().eventId(id).eventType("matricula.retirada")
            .occurredAt(LocalDateTime.now().plusHours(2)).payload(ev).build();
        matriculaLocal.procesar(sobre);
        matriculaLocal.procesar(sobre);                                                      // duplicado
        assertThat(jdbc.queryForObject("select estado from matriculas_local where matricula_id = 2", String.class)).isEqualTo("RETIRADO");

        // un evento anterior ("confirmada" de hace un día) llega tarde: se ignora
        ev.setEstado("MATRICULADO");
        matriculaLocal.procesar(EventoSobre.<MatriculaEvent>builder().eventId(UUID.randomUUID()).eventType("matricula.confirmada")
            .occurredAt(LocalDateTime.now().minusDays(1)).payload(ev).build());
        assertThat(jdbc.queryForObject("select estado from matriculas_local where matricula_id = 2", String.class)).isEqualTo("RETIRADO");
        assertThat(abrirDia(hoy).alumnos()).hasSize(3);
    }

    @Test
    void conLaReplicaVaciaSeHaceLaCargaInicialDesdeMatricula() {
        jdbc.execute("TRUNCATE matriculas_local");
        when(matriculaClient.deSeccion(SECCION)).thenReturn(List.of(
            new MatriculaClient.MatriculaInterna(1L, 1L, "Alumno 1", ANIO, 1L, 3L, SECCION, "MATRICULADO", "NUEVA"),
            new MatriculaClient.MatriculaInterna(2L, 2L, "Alumno 2", ANIO, 1L, 3L, SECCION, "PRE_MATRICULA", "NUEVA")));
        assertThat(abrirDia(hoy).alumnos()).hasSize(1);            // la pre-matrícula aún no asiste
    }

    // ------------------------------------------------------------------ justificaciones

    JwtRequestPostProcessor usuario(long id, String rol) {
        return jwt().jwt(j -> j.claim("usuarioId", id).claim("realm_access", Map.of("roles", List.of(rol))))
            .authorities(new SimpleGrantedAuthority("ROLE_" + rol));
    }

    @Test
    void aprobarJustificacionConvierteExactamenteLasFaltasDelRango() throws Exception {
        for (int d = 4; d >= 1; d--) {                              // 4 días con falta del alumno 1
            SesionResponse s = abrirDia(hoy.minusDays(d));
            registro.registrar(s.id(), lote("FALTA", "PRESENTE", "PRESENTE", "PRESENTE"), USUARIO_DOCENTE, false);
        }
        when(alumnos.hijosDeUsuario(700L)).thenReturn(List.of(new AlumnosClient.Hijo(1L, "A-1", "Alumno 1", "ACTIVO", "PADRE")));
        String body = """
            {"alumnoId":1,"fechaDesde":"%s","fechaHasta":"%s","tipo":"MEDICA","motivo":"Reposo médico"}
            """.formatted(hoy.minusDays(3), hoy.minusDays(2));
        String respuesta = mvc.perform(post("/api/v1/asistencia/justificaciones").with(usuario(700, "APODERADO"))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.estado").value("PENDIENTE")).andReturn().getResponse().getContentAsString();
        long id = ((Number) com.jayway.jsonpath.JsonPath.read(respuesta, "$.id")).longValue();

        // el apoderado no puede resolverla
        mvc.perform(patch("/api/v1/asistencia/justificaciones/" + id + "/resolver").with(usuario(700, "APODERADO"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"resolucion\":\"APROBADA\"}"))
            .andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/asistencia/justificaciones/" + id + "/resolver").with(usuario(8, "SECRETARIA"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"resolucion\":\"APROBADA\",\"comentario\":\"Certificado válido\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.faltasJustificadas").value(2));

        assertThat(jdbc.queryForObject("select count(*) from asistencia_detalle where estado = 'JUSTIFICADO'", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from asistencia_detalle where estado = 'FALTA'", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from asistencia_auditoria", Integer.class)).isEqualTo(2);
        verify(kafka).send(eq(KafkaConfig.TOPIC_JUSTIFICACION_RESUELTA), eq(String.valueOf(id)), contains("\"resultado\":\"APROBADA\""));
        // ya resuelta: no se resuelve dos veces
        mvc.perform(patch("/api/v1/asistencia/justificaciones/" + id + "/resolver").with(usuario(8, "SECRETARIA"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"resolucion\":\"RECHAZADA\"}"))
            .andExpect(status().isConflict());
    }

    @Test
    void rechazarNoTocaLasFaltas() {
        SesionResponse s = abrirDia(hoy.minusDays(1));
        registro.registrar(s.id(), lote("FALTA", "PRESENTE", "PRESENTE", "PRESENTE"), USUARIO_DOCENTE, false);
        jdbc.update("""
            insert into justificaciones (matricula_id, alumno_id, fecha_desde, fecha_hasta, tipo, motivo, solicitado_por)
            values (1, 1, ?, ?, 'FAMILIAR', 'Viaje', 700)
            """, hoy.minusDays(2), hoy);
        Long id = jdbc.queryForObject("select id from justificaciones", Long.class);
        JustificacionResponse r = justificaciones.resolver(id, new ResolverJustificacionRequest("RECHAZADA", "Sin sustento"), 8L);
        assertThat(r.estado()).isEqualTo("RECHAZADA");
        assertThat(r.faltasJustificadas()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from asistencia_detalle where estado = 'FALTA'", Integer.class)).isEqualTo(1);
    }

    @Test
    void apoderadoNoPuedeJustificarAlumnoAjeno() throws Exception {
        when(alumnos.hijosDeUsuario(700L)).thenReturn(List.of(new AlumnosClient.Hijo(1L, "A-1", "Alumno 1", "ACTIVO", "PADRE")));
        String ajeno = """
            {"alumnoId":2,"fechaDesde":"%s","fechaHasta":"%s","tipo":"OTRO","motivo":"x"}
            """.formatted(hoy.minusDays(1), hoy);
        mvc.perform(post("/api/v1/asistencia/justificaciones").with(usuario(700, "APODERADO"))
                .contentType(MediaType.APPLICATION_JSON).content(ajeno))
            .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ reportes y API

    @Test
    void reportesAlertanAlumnosConExcesoDeFaltas() {
        for (int d = 5; d >= 1; d--) {
            SesionResponse s = abrirDia(hoy.minusDays(d));
            String primero = d >= 3 ? "FALTA" : "PRESENTE";          // alumno 1 falta 3 de 5 días (60 %)
            registro.registrar(s.id(), lote(primero, "PRESENTE", "TARDANZA", "PRESENTE"), USUARIO_DOCENTE, false);
        }
        ReporteSeccionResponse r = reportes.reporteSeccion(SECCION, hoy.minusDays(10), hoy);
        assertThat(r.totalAlumnos()).isEqualTo(4);
        assertThat(r.alertasExcesoFaltas()).hasSize(1);
        assertThat(r.alertasExcesoFaltas().get(0).alumnoId()).isEqualTo(1L);
        assertThat(r.alertasExcesoFaltas().get(0).dias().faltas()).isEqualTo(3);
        ResumenAlumnoResponse res = reportes.resumenAlumno(3L, null, hoy.minusDays(10), hoy);
        assertThat(res.dias().tardanzas()).isEqualTo(5);
        assertThat(res.dias().porcentajeAsistencia()).isEqualTo(100.0);
    }

    @Test
    void apoderadoVeSoloAsistenciaDeSusHijos() throws Exception {
        when(alumnos.hijosDeUsuario(700L)).thenReturn(List.of(new AlumnosClient.Hijo(1L, "A-1", "Alumno 1", "ACTIVO", "PADRE")));
        SesionResponse s = abrirDia(hoy);
        registro.registrar(s.id(), lote("FALTA", "PRESENTE", "PRESENTE", "PRESENTE"), USUARIO_DOCENTE, false);
        mvc.perform(get("/api/v1/asistencia/mias").with(usuario(700, "APODERADO")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].resumen.dias.faltas").value(1));
        mvc.perform(get("/api/v1/asistencia/alumnos/1/resumen").with(usuario(700, "APODERADO"))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/asistencia/alumnos/2/resumen").with(usuario(700, "APODERADO"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/asistencia/secciones/10/reporte").with(usuario(700, "APODERADO"))).andExpect(status().isForbidden());
    }

    @Test
    void conPlanificacionCaidaSeBloqueaElRegistroPeroSePuedenConsultarReportes() throws Exception {
        SesionResponse s = abrirDia(hoy);
        when(planificacion.esHabil(any())).thenThrow(new ServicioNoDisponibleException("planificacion-ms caído"));
        mvc.perform(post("/api/v1/asistencia/sesiones").with(usuario(USUARIO_DOCENTE, "DOCENTE"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"seccionId\":10,\"fecha\":\"%s\",\"tipo\":\"DIA\"}".formatted(hoy.minusDays(1))))
            .andExpect(status().isServiceUnavailable());
        mvc.perform(get("/api/v1/asistencia/secciones/10/reporte").with(usuario(8, "SECRETARIA"))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/asistencia/sesiones").param("seccionId", "10").param("fecha", hoy.toString()).with(usuario(8, "SECRETARIA")))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(s.id()));
    }

    @Test
    void soloDocenteYAdminAbrenSesionesPorApi() throws Exception {
        String body = "{\"seccionId\":10,\"fecha\":\"%s\",\"tipo\":\"DIA\"}".formatted(hoy);
        mvc.perform(post("/api/v1/asistencia/sesiones").with(usuario(8, "SECRETARIA")).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/asistencia/sesiones").with(usuario(USUARIO_DOCENTE, "DOCENTE")).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.alumnos.length()").value(4));
    }
}
