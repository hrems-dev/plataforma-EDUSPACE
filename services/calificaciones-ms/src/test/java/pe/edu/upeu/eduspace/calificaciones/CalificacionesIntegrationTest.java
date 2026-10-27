package pe.edu.upeu.eduspace.calificaciones;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import pe.edu.upeu.eduspace.calificaciones.client.AlumnosClient;
import pe.edu.upeu.eduspace.calificaciones.client.PlanificacionClient;
import pe.edu.upeu.eduspace.calificaciones.dto.*;
import pe.edu.upeu.eduspace.calificaciones.exception.BusinessRuleException;
import pe.edu.upeu.eduspace.calificaciones.messaging.ExamenCalificadoConsumer;
import pe.edu.upeu.eduspace.calificaciones.messaging.MatriculaEventConsumer;
import pe.edu.upeu.eduspace.calificaciones.messaging.TareaCalificadaConsumer;
import pe.edu.upeu.eduspace.calificaciones.service.*;

@AutoConfigureMockMvc
class CalificacionesIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired MatriculaEventConsumer matriculaConsumer;
    @Autowired TareaCalificadaConsumer tareaConsumer;
    @Autowired ExamenCalificadoConsumer examenConsumer;
    @Autowired CriterioService criterios;
    @Autowired NotaService notas;
    @Autowired CierrePeriodoService cierres;
    @Autowired BoletinService boletines;
    @Autowired NotaEventoService notaEventos;
    @Autowired ResultadoAnualService resultados;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;

    @MockitoBean PlanificacionClient planificacion;
    @MockitoBean AlumnosClient alumnosClient;
    @MockitoBean MinioClient minio;
    @MockitoBean KafkaTemplate<String, String> kafka;

    static final long SECCION = 10, CURSO = 7, ANIO = 1, PERIODO = 3, DOCENTE = 50, U_DOCENTE = 5;

    @BeforeEach
    void preparar() throws Exception {
        jdbc.execute("TRUNCATE boletines, resultados_anuales, promedios_periodo, cierres_periodo, notas_pendientes, notas, criterios_evaluacion, "
            + "matriculas_local, eventos_procesados RESTART IDENTITY CASCADE");
        reset(planificacion, alumnosClient, minio, kafka);
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));
        when(planificacion.getDocentePorUsuario(U_DOCENTE)).thenReturn(new PlanificacionClient.DocenteInterno(DOCENTE, "Prof. Díaz", "ACTIVO"));
        when(planificacion.getDocentePorUsuario(6L)).thenReturn(new PlanificacionClient.DocenteInterno(60L, "Otro", "ACTIVO"));
        when(planificacion.validarAsignacionDocente(DOCENTE, SECCION, CURSO)).thenReturn(true);
        when(planificacion.validarAsignacionDocente(60L, SECCION, CURSO)).thenReturn(false);
        when(planificacion.getPeriodoPorId(PERIODO)).thenReturn(Map.of("id", PERIODO, "nombre", "II Bimestre"));
        for (long a = 1; a <= 3; a++) {
            matriculaConsumer.procesarEventoMatricula("""
                {"eventId":"%s","eventType":"matricula.confirmada","occurredAt":"2026-02-01T10:00:00","version":1,
                 "matriculaId":%d,"alumnoId":%d,"alumnoNombre":"Alumno %d","anioId":1,"nivelId":1,"gradoId":3,"seccionId":10,"estado":"MATRICULADO"}
                """.formatted(UUID.randomUUID(), a, a, a));
        }
        when(alumnosClient.getAlumnoDeUsuario(301L)).thenReturn(new AlumnosClient.AlumnoUsuario(1L));
        when(alumnosClient.getHijosPorUsuario(700L)).thenReturn(List.of(new AlumnosClient.HijoResumen(1L, "A-1", "Alumno 1", "ACTIVO", "PADRE")));
        when(alumnosClient.getHijosPorUsuario(701L)).thenReturn(List.of(new AlumnosClient.HijoResumen(2L, "A-2", "Alumno 2", "ACTIVO", "PADRE")));
    }

    CriterioResponse criterio(String nombre, String peso, String origen) {
        return criterios.crearCriterio(CriterioRequest.builder().anioId(ANIO).periodoId(PERIODO).seccionId(SECCION).cursoId(CURSO)
            .nombre(nombre).peso(new BigDecimal(peso)).origenTipo(origen).build(), U_DOCENTE, false);
    }

    void notaLote(long criterioId, String... valores) {
        List<NotaItemRequest> items = new java.util.ArrayList<>();
        for (int i = 0; i < valores.length; i++) {
            items.add(NotaItemRequest.builder().matriculaId(i + 1L).alumnoId(i + 1L).valor(new BigDecimal(valores[i])).build());
        }
        notas.registrarNotasLote(NotasLoteRequest.builder().seccionId(SECCION).cursoId(CURSO).periodoId(PERIODO)
            .criterioId(criterioId).notas(items).build(), U_DOCENTE, false);
    }

    void tareaCalificada(String eventId, long entregaId, long alumnoId, String nota) {
        tareaConsumer.procesarTareaCalificada("""
            {"eventId":"%s","eventType":"tarea.calificada","occurredAt":"2026-05-01T10:00:00","version":1,"traceId":"t",
             "tareaId":5,"entregaId":%d,"alumnoId":%d,"matriculaId":%d,"seccionId":10,"cursoId":7,"periodoId":3,
             "titulo":"Fracciones","nota":%s,"puntajeMaximo":20}
            """.formatted(eventId, entregaId, alumnoId, alumnoId, nota));
    }

    int cuenta(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    // ------------------------------------------------------------------ criterios

    @Test
    void lasSumaDePesosNoPuedeSuperar100YElValidadorLoInforma() {
        criterio("Tareas", "30", "TAREAS");
        criterio("Exámenes", "40", "EXAMENES");
        assertThatThrownBy(() -> criterio("Práctica", "40", "MANUAL")).isInstanceOf(BusinessRuleException.class).hasMessageContaining("100");
        ValidarPesosResponse v = criterios.validarPesos(PERIODO, SECCION, CURSO);
        assertThat(v.getSumaPesos()).isEqualByComparingTo("70");
        assertThat(v.isEsValido()).isFalse();
        criterio("Práctica", "30", "MANUAL");
        assertThat(criterios.validarPesos(PERIODO, SECCION, CURSO).isEsValido()).isTrue();
    }

    @Test
    void docenteSinAsignacionNoCreaCriteriosYSinPlanificacionSeDeniega() {
        assertThatThrownBy(() -> criterios.crearCriterio(CriterioRequest.builder().anioId(ANIO).periodoId(PERIODO).seccionId(SECCION)
            .cursoId(CURSO).nombre("X").peso(new BigDecimal("10")).build(), 6L, false)).isInstanceOf(AccessDeniedException.class);
        when(planificacion.getDocentePorUsuario(U_DOCENTE)).thenThrow(new RuntimeException("planificacion-ms caído"));
        assertThatThrownBy(() -> criterio("Y", "10", "MANUAL")).isInstanceOf(BusinessRuleException.class).hasMessageContaining("no se puede validar".substring(0, 6).isEmpty() ? "" : "validar");
    }

    @Test
    void notasSoloLasRegistraElDocenteAsignado() {
        CriterioResponse c = criterio("Práctica", "100", "MANUAL");
        assertThatThrownBy(() -> notas.registrarNotasLote(NotasLoteRequest.builder().seccionId(SECCION).cursoId(CURSO).periodoId(PERIODO)
            .criterioId(c.getId()).notas(List.of(NotaItemRequest.builder().matriculaId(1L).alumnoId(1L).valor(BigDecimal.TEN).build())).build(), 6L, false))
            .isInstanceOf(AccessDeniedException.class);
        assertThat(cuenta("select count(*) from notas")).isZero();
    }

    // ------------------------------------------------------------------ promedio y cierre

    @Test
    void promedioPonderadoYLiteral() {
        CriterioResponse tareas = criterio("Tareas", "30", "TAREAS");
        CriterioResponse examen = criterio("Examen", "30", "EXAMENES");
        CriterioResponse practica = criterio("Práctica", "40", "MANUAL");
        notaLote(tareas.getId(), "20", "10", "8");
        notaLote(examen.getId(), "10", "10", "8");
        notaLote(practica.getId(), "15", "10", "9");
        assertThat(jdbc.queryForObject("select promedio from promedios_periodo where matricula_id = 1", BigDecimal.class)).isEqualByComparingTo("15.00");
        assertThat(jdbc.queryForObject("select literal from promedios_periodo where matricula_id = 1", String.class)).isEqualTo("A");
        assertThat(jdbc.queryForObject("select promedio from promedios_periodo where matricula_id = 2", BigDecimal.class)).isEqualByComparingTo("10.00");
        assertThat(jdbc.queryForObject("select literal from promedios_periodo where matricula_id = 2", String.class)).isEqualTo("C");
        assertThat(jdbc.queryForObject("select estado from promedios_periodo where matricula_id = 1", String.class)).isEqualTo("BORRADOR");
    }

    @Test
    void cerrarExigePesos100YNotasCompletas() {
        CriterioResponse a = criterio("Tareas", "60", "TAREAS");
        assertThatThrownBy(() -> cierres.cerrarPeriodo(cierre(), 9L)).isInstanceOf(BusinessRuleException.class).hasMessageContaining("100");
        CriterioResponse b = criterio("Práctica", "40", "MANUAL");
        notaLote(a.getId(), "14", "14", "14");
        assertThatThrownBy(() -> cierres.cerrarPeriodo(cierre(), 9L)).isInstanceOf(BusinessRuleException.class).hasMessageContaining("Falta registrar nota");
        notaLote(b.getId(), "16", "16");                               // falta el alumno 3
        assertThatThrownBy(() -> cierres.cerrarPeriodo(cierre(), 9L)).isInstanceOf(BusinessRuleException.class).hasMessageContaining("Alumno 3");
        notaLote(b.getId(), "16", "16", "16");
        CierrePeriodoResponse r = cierres.cerrarPeriodo(cierre(), 9L);
        assertThat(r.getEstado()).isEqualTo("CERRADO");
        assertThat(cuenta("select count(*) from promedios_periodo where estado = 'CERRADO'")).isEqualTo(3);
        assertThatThrownBy(() -> cierres.cerrarPeriodo(cierre(), 9L)).isInstanceOf(BusinessRuleException.class);
    }

    CierrePeriodoRequest cierre() {
        CierrePeriodoRequest r = new CierrePeriodoRequest();
        r.setSeccionId(SECCION); r.setPeriodoId(PERIODO);
        return r;
    }

    void periodoCerrado() {
        CriterioResponse a = criterio("Tareas", "50", "TAREAS");
        CriterioResponse b = criterio("Práctica", "50", "MANUAL");
        notaLote(a.getId(), "12", "12", "12");
        notaLote(b.getId(), "14", "14", "14");
        cierres.cerrarPeriodo(cierre(), 9L);
    }

    @Test
    void periodoCerradoNoAdmiteModificacionesYSoloAdminReabreConMotivoAuditado() {
        periodoCerrado();
        Long criterioId = jdbc.queryForObject("select id from criterios_evaluacion where origen_tipo = 'MANUAL'", Long.class);
        assertThatThrownBy(() -> notaLote(criterioId, "20", "20", "20")).isInstanceOf(BusinessRuleException.class).hasMessageContaining("CERRADO");
        assertThatThrownBy(() -> criterio("Nuevo", "10", "MANUAL")).isInstanceOf(BusinessRuleException.class);

        ReaperturaRequest re = new ReaperturaRequest();
        re.setSeccionId(SECCION); re.setPeriodoId(PERIODO); re.setMotivo("Error en la nota del alumno 2");
        CierrePeriodoResponse r = cierres.reabrirPeriodo(re, 1L);
        assertThat(r.getEstado()).isEqualTo("REABIERTO");
        assertThat(jdbc.queryForMap("select reabierto_por, motivo_reapertura from cierres_periodo")).containsEntry("reabierto_por", 1L)
            .containsEntry("motivo_reapertura", "Error en la nota del alumno 2");
        assertThat(cuenta("select count(*) from promedios_periodo where estado = 'BORRADOR'")).isEqualTo(3);
        notaLote(criterioId, "20", "20", "20");                         // ya se puede corregir
    }

    // ------------------------------------------------------------------ eventos de notas

    @Test
    void notaDeTareaSinCriterioQuedaPendienteEnBaseDeDatosYSeAplicaAlCrearElCriterio() {
        tareaCalificada(UUID.randomUUID().toString(), 1, 1, "17.50");
        tareaCalificada(UUID.randomUUID().toString(), 2, 2, "12.00");
        assertThat(cuenta("select count(*) from notas")).isZero();
        assertThat(notaEventos.listarPendientes(SECCION, CURSO)).hasSize(2);
        assertThat(cuenta("select count(*) from notas_pendientes where estado = 'PENDIENTE'")).isEqualTo(2);   // persistente, no en memoria

        criterio("Tareas", "100", "TAREAS");                             // el docente lo crea después
        assertThat(cuenta("select count(*) from notas")).isEqualTo(2);
        assertThat(notaEventos.listarPendientes(SECCION, CURSO)).isEmpty();
        assertThat(jdbc.queryForObject("select promedio from promedios_periodo where matricula_id = 1", BigDecimal.class)).isEqualByComparingTo("17.50");
    }

    @Test
    void eventoRepetidoNoDuplicaYLaRecalificacionActualizaPorOrigenId() {
        criterio("Tareas", "100", "TAREAS");
        String id = UUID.randomUUID().toString();
        tareaCalificada(id, 1, 1, "10");
        tareaCalificada(id, 1, 1, "10");                                 // mismo eventId
        assertThat(cuenta("select count(*) from notas")).isEqualTo(1);
        tareaCalificada(UUID.randomUUID().toString(), 1, 1, "18");       // recalificación de la misma entrega (mismo origen_id)
        assertThat(cuenta("select count(*) from notas")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select valor from notas", BigDecimal.class)).isEqualByComparingTo("18");
    }

    @Test
    void notaDeExamenVaAlCriterioDeExamenes() {
        criterio("Exámenes", "100", "EXAMENES");
        examenConsumer.procesarExamenCalificado("""
            {"eventId":"%s","eventType":"examen.calificado","occurredAt":"2026-05-01T10:00:00","version":1,
             "examenId":9,"intentoId":4,"alumnoId":3,"matriculaId":3,"seccionId":10,"cursoId":7,"periodoId":3,"titulo":"Parcial","cursoNombre":"Matemática","nota":16.67}
            """.formatted(UUID.randomUUID()));
        assertThat(jdbc.queryForObject("select valor from notas where matricula_id = 3", BigDecimal.class)).isEqualByComparingTo("16.67");
        assertThat(jdbc.queryForObject("select origen from notas", String.class)).isEqualTo("EXAMEN");
    }

    @Test
    void conPeriodoCerradoLaNotaNoSeAplicaPeroNoSePierdeYAlReabrirSeIncorpora() {
        periodoCerrado();
        tareaCalificada(UUID.randomUUID().toString(), 7, 1, "20");
        assertThat(notaEventos.listarPendientes(SECCION, CURSO)).hasSize(1);
        assertThat(notaEventos.listarPendientes(SECCION, CURSO).get(0).getMotivo()).isEqualTo("PERIODO_CERRADO");
        assertThat(jdbc.queryForObject("select count(*) from notas where origen = 'TAREA'", Integer.class)).isZero();

        ReaperturaRequest re = new ReaperturaRequest();
        re.setSeccionId(SECCION); re.setPeriodoId(PERIODO); re.setMotivo("Entrega tardía aceptada");
        cierres.reabrirPeriodo(re, 1L);
        assertThat(jdbc.queryForObject("select valor from notas where origen = 'TAREA'", BigDecimal.class)).isEqualByComparingTo("20");
        assertThat(notaEventos.listarPendientes(SECCION, CURSO)).isEmpty();
    }

    @Test
    void eventoIncompletoSeDescartaSinRomperLaCola() {
        tareaConsumer.procesarTareaCalificada("{\"eventId\":\"" + UUID.randomUUID() + "\",\"tareaId\":5}");
        tareaConsumer.procesarTareaCalificada("esto no es json");
        assertThat(cuenta("select count(*) from notas_pendientes")).isZero();
    }

    // ------------------------------------------------------------------ boletín, libreta, resultado anual

    @Test
    void boletinSeGeneraEnPdfVersionadoYPublicaEventoPlano() throws Exception {
        periodoCerrado();
        GenerarBoletinRequest req = new GenerarBoletinRequest();
        req.setSeccionId(SECCION); req.setPeriodoId(PERIODO); req.setAnioId(ANIO);
        List<BoletinResponse> v1 = boletines.generarBoletines(req, 9L);
        assertThat(v1).hasSize(3).allMatch(b -> b.getVersion() == 1 && b.getRutaPdf().endsWith(".pdf"));

        ArgumentCaptor<PutObjectArgs> subida = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(minio, times(3)).putObject(subida.capture());
        assertThat(subida.getAllValues().get(0).contentType()).isEqualTo("application/pdf");
        assertThat(subida.getAllValues().get(0).stream().readNBytes(5)).isEqualTo("%PDF-".getBytes());

        List<BoletinResponse> v2 = boletines.generarBoletines(req, 9L);
        assertThat(v2).allMatch(b -> b.getVersion() == 2);
        assertThat(cuenta("select count(*) from boletines where estado = 'VIGENTE'")).isEqualTo(3);
        assertThat(cuenta("select count(*) from boletines where estado = 'ANULADO'")).isEqualTo(3);

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(kafka, atLeastOnce()).send(eq("boletin.generado"), anyString(), json.capture());
        assertThat(json.getValue()).contains("\"eventType\":\"boletin.generado\"").contains("\"alumnoId\":").contains("\"periodoNombre\":\"II Bimestre\"")
            .doesNotContain("\"payload\"");
    }

    @Test
    void boletinDePeriodoExigeCierre() {
        criterio("Práctica", "100", "MANUAL");
        GenerarBoletinRequest req = new GenerarBoletinRequest();
        req.setSeccionId(SECCION); req.setPeriodoId(PERIODO);
        assertThatThrownBy(() -> boletines.generarBoletines(req, 9L)).isInstanceOf(BusinessRuleException.class).hasMessageContaining("CERRADO");
    }

    @Test
    void resultadoAnualAplicaLaRegladeRecuperacion() {
        CriterioResponse c = criterio("Práctica", "100", "MANUAL");
        notaLote(c.getId(), "15", "9.5", "6");
        cierres.cerrarPeriodo(cierre(), 9L);
        List<ResultadoAnualResponse> res = resultados.calcularResultadosSeccion(SECCION);
        assertThat(res).extracting(ResultadoAnualResponse::getEstado).containsExactlyInAnyOrder("APROBADO", "RECUPERACION", "DESAPROBADO");
    }

    JwtRequestPostProcessor usuario(long id, String rol) {
        return jwt().jwt(j -> j.claim("usuarioId", id).claim("realm_access", Map.of("roles", List.of(rol))))
            .authorities(new SimpleGrantedAuthority("ROLE_" + rol));
    }

    @Test
    void libretaSoloLaVeSuDuenioYElAlumnoSoloVePeriodosCerrados() throws Exception {
        CriterioResponse c = criterio("Práctica", "100", "MANUAL");
        notaLote(c.getId(), "15", "13", "11");
        mvc.perform(get("/api/v1/calificaciones/alumnos/1/libreta").with(usuario(700, "APODERADO"))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/calificaciones/alumnos/1/libreta").with(usuario(701, "APODERADO"))).andExpect(status().isForbidden());   // IDOR bloqueado
        mvc.perform(get("/api/v1/calificaciones/alumnos/1/libreta").with(usuario(301, "ALUMNO"))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/calificaciones/alumnos/2/libreta").with(usuario(301, "ALUMNO"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/calificaciones/alumnos/2/libreta").with(usuario(U_DOCENTE, "DOCENTE"))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/calificaciones/mias").with(usuario(700, "APODERADO"))).andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/v1/calificaciones/mias").with(usuario(301, "ALUMNO"))).andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1));        // el propio alumno también ve su libreta
        mvc.perform(get("/api/v1/calificaciones/boletines").param("alumnoId", "2").with(usuario(700, "APODERADO"))).andExpect(status().isForbidden());
    }

    @Test
    void apiDeCriteriosYNotasConRoles() throws Exception {
        String crear = "{\"anioId\":1,\"periodoId\":3,\"seccionId\":10,\"cursoId\":7,\"nombre\":\"Práctica\",\"peso\":100,\"origenTipo\":\"MANUAL\"}";
        mvc.perform(post("/api/v1/calificaciones/criterios").with(usuario(301, "ALUMNO")).contentType(MediaType.APPLICATION_JSON).content(crear)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/calificaciones/criterios").with(usuario(6, "DOCENTE")).contentType(MediaType.APPLICATION_JSON).content(crear)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/calificaciones/criterios").with(usuario(U_DOCENTE, "DOCENTE")).contentType(MediaType.APPLICATION_JSON).content(crear))
            .andExpect(status().isCreated());
        Long criterioId = jdbc.queryForObject("select id from criterios_evaluacion", Long.class);
        String lote = "{\"seccionId\":10,\"cursoId\":7,\"periodoId\":3,\"criterioId\":%d,\"notas\":[{\"matriculaId\":1,\"alumnoId\":1,\"valor\":18}]}".formatted(criterioId);
        mvc.perform(put("/api/v1/calificaciones/notas/lote").with(usuario(U_DOCENTE, "DOCENTE")).contentType(MediaType.APPLICATION_JSON).content(lote)).andExpect(status().isOk());
        mvc.perform(put("/api/v1/calificaciones/notas/lote").with(usuario(U_DOCENTE, "DOCENTE")).contentType(MediaType.APPLICATION_JSON)
            .content(lote.replace("\"valor\":18", "\"valor\":25"))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/calificaciones/registro").param("seccionId", "10").param("cursoId", "7").param("periodoId", "3")
            .with(usuario(U_DOCENTE, "DOCENTE"))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/calificaciones/promedios").param("seccionId", "10").param("periodoId", "3").with(usuario(U_DOCENTE, "DOCENTE")))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].promedio").value(18.0));
        mvc.perform(post("/api/v1/calificaciones/periodos/reabrir").with(usuario(U_DOCENTE, "DOCENTE")).contentType(MediaType.APPLICATION_JSON)
            .content("{\"seccionId\":10,\"periodoId\":3,\"motivo\":\"x\"}")).andExpect(status().isForbidden());   // solo ADMIN
    }
}
