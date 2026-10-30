package pe.edu.upeu.eduspace.matricula;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pe.edu.upeu.eduspace.matricula.client.*;
import pe.edu.upeu.eduspace.matricula.dto.Dtos.*;
import pe.edu.upeu.eduspace.matricula.exception.BusinessRuleException;
import pe.edu.upeu.eduspace.matricula.messaging.OutboxPublisher;
import pe.edu.upeu.eduspace.matricula.messaging.PagoEventConsumer;
import pe.edu.upeu.eduspace.matricula.scheduler.VencimientoJob;
import pe.edu.upeu.eduspace.matricula.service.CampaniaService;
import pe.edu.upeu.eduspace.matricula.service.MatriculaService;
import pe.edu.upeu.eduspace.matricula.service.RatificacionService;

/** Flujo completo de matrícula con PostgreSQL real; los servicios vecinos y Kafka se simulan. */
@AutoConfigureMockMvc
class MatriculaIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired MatriculaService service;
    @Autowired CampaniaService campanias;
    @Autowired RatificacionService ratificacion;
    @Autowired PagoEventConsumer pagoConsumer;
    @Autowired VencimientoJob vencimientoJob;
    @Autowired OutboxPublisher outboxPublisher;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;

    @MockitoBean AlumnosGateway alumnos;
    @MockitoBean PlanificacionGateway planificacion;
    @MockitoBean PagoClient pagoClient;
    @MockitoBean KafkaTemplate<String, String> kafka;

    static final long ANIO = 1L, GRADO = 3L, NIVEL = 1L;

    @BeforeEach
    void limpiar() {
        jdbc.execute("TRUNCATE matriculas, cupos_seccion, campanias_matricula, outbox_eventos, eventos_procesados, "
            + "matricula_historial, matricula_requisitos RESTART IDENTITY CASCADE");
        jdbc.execute("ALTER SEQUENCE matricula_codigo_seq RESTART WITH 1");
        reset(alumnos, planificacion, pagoClient, kafka);
        when(planificacion.anio(ANIO)).thenReturn(new PlanificacionClient.Anio(ANIO, "2026", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 12, 20), "ACTIVO"));
        when(planificacion.grado(GRADO)).thenReturn(new PlanificacionClient.Grado(GRADO, NIVEL, "Primaria", (short) 3, "3° Primaria", true));
        seccion(10L, "A", 2);
        for (long a = 1; a <= 6; a++) alumnoActivo(a);
        var abierta = campanias.crear(new CampaniaRequest(ANIO, "Matrícula 2026", LocalDate.now().minusDays(5), LocalDate.now().plusDays(30)));
        campanias.abrir(abierta.id());
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));
    }

    void seccion(long id, String nombre, int capacidad) {
        var s = new PlanificacionClient.Seccion(id, nombre, GRADO, NIVEL, ANIO, (short) capacidad, "MAÑANA", null);
        when(planificacion.seccion(id)).thenReturn(s);
    }

    void alumnoActivo(long id) {
        when(alumnos.alumno(id)).thenReturn(new AlumnosClient.AlumnoBasico(id, "A-" + id, "Alumno " + id, "ACTIVO"));
        when(alumnos.expediente(id)).thenReturn(new AlumnosClient.Expediente(true, List.of()));
        when(alumnos.responsablePagoOpcional(id)).thenReturn(new AlumnosClient.ResponsablePago(100 + id, 500 + id, "Apoderado " + id));
    }

    MatriculaResponse crear(long alumnoId, long seccionId) {
        return service.crear(new MatriculaCreateRequest(alumnoId, seccionId, "NUEVA", null), 9L);
    }

    int ocupados(long seccionId) {
        return jdbc.queryForObject("select ocupados from cupos_seccion where seccion_id = ?", Integer.class, seccionId);
    }

    int eventos(String topic) {
        return jdbc.queryForObject("select count(*) from outbox_eventos where topic = ?", Integer.class, topic);
    }

    // ------------------------------------------------------------------ creación

    @Test
    void creaPreMatriculaConCupoHistorialRequisitosYEventoPlano() {
        MatriculaResponse m = crear(1, 10);

        assertThat(m.estado()).isEqualTo("PRE_MATRICULA");
        assertThat(m.codigo()).isEqualTo("MAT-2026-000001");
        assertThat(m.venceEn()).isNotNull();
        assertThat(ocupados(10)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from matricula_requisitos where matricula_id = ?", Integer.class, m.id())).isEqualTo(4);
        assertThat(jdbc.queryForObject("select tipo_cambio from matricula_historial where matricula_id = ?", String.class, m.id())).isEqualTo("CREACION");

        // El sobre es PLANO: eventId/eventType junto a los campos de negocio, sin "payload" anidado
        Map<String, Object> ev = jdbc.queryForMap("select payload::text as p from outbox_eventos where topic = 'matricula.creada'");
        String json = ev.get("p").toString();
        assertThat(json).contains("\"eventId\"").contains("\"eventType\": \"matricula.creada\"").contains("\"alumnoId\": 1")
            .contains("\"responsablePagoUsuarioId\": 501").contains("\"gradoNombre\": \"3° Primaria\"").contains("\"anio\": \"2026\"")
            .doesNotContain("\"payload\"");
    }

    @Test
    void sinCampaniaAbiertaNoSeMatricula() {
        jdbc.update("update campanias_matricula set estado = 'CERRADA'");
        assertThatThrownBy(() -> crear(1, 10)).isInstanceOf(BusinessRuleException.class).hasMessageContaining("campaña");
        assertThat(ocupados10Existe()).isFalse();
    }

    boolean ocupados10Existe() {
        return jdbc.queryForObject("select count(*) from cupos_seccion", Integer.class) > 0;
    }

    @Test
    void elMismoAlumnoNoPuedeMatricularseDosVecesEnElAnio() {
        crear(1, 10);
        assertThatThrownBy(() -> crear(1, 10)).isInstanceOf(BusinessRuleException.class).hasMessageContaining("ya tiene");
        assertThat(ocupados(10)).isEqualTo(1);
    }

    @Test
    void expedienteIncompletoSeRechazaConLaListaDeFaltantes() {
        when(alumnos.expediente(2L)).thenReturn(new AlumnosClient.Expediente(false, List.of("DNI", "PARTIDA_NACIMIENTO")));
        assertThatThrownBy(() -> crear(2, 10)).isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("DNI").hasMessageContaining("PARTIDA_NACIMIENTO");
        assertThat(jdbc.queryForObject("select count(*) from matriculas", Integer.class)).isZero();
    }

    @Test
    void alumnoInactivoSeRechaza() {
        when(alumnos.alumno(3L)).thenReturn(new AlumnosClient.AlumnoBasico(3L, "A-3", "Alumno 3", "RETIRADO"));
        assertThatThrownBy(() -> crear(3, 10)).isInstanceOf(BusinessRuleException.class).hasMessageContaining("ACTIVO");
    }

    @Test
    void dosSolicitudesSimultaneasPorElUltimoCupo_unaGanaLaOtraRecibe409() throws Exception {
        seccion(11L, "B", 1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch salida = new CountDownLatch(1);
        List<Future<Object>> res = List.of(
            pool.submit(() -> { salida.await(); return intentar(1, 11); }),
            pool.submit(() -> { salida.await(); return intentar(2, 11); }));
        salida.countDown();
        int ok = 0, sinCupo = 0;
        for (Future<Object> f : res) {
            Object r = f.get(30, TimeUnit.SECONDS);
            if (r instanceof MatriculaResponse) ok++;
            else if (r instanceof BusinessRuleException) sinCupo++;
        }
        pool.shutdown();
        assertThat(ok).isEqualTo(1);
        assertThat(sinCupo).isEqualTo(1);
        assertThat(ocupados(11)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from matriculas where seccion_id = 11", Integer.class)).isEqualTo(1);
    }

    Object intentar(long alumno, long seccion) {
        try {
            return crear(alumno, seccion);
        } catch (Exception e) {
            return e;
        }
    }

    // ------------------------------------------------------------------ confirmación por pago

    String pagoJson(String eventId, long matriculaId, String concepto, String tipo) {
        return """
            {"eventId":"%s","eventType":"%s","occurredAt":"2026-05-01T10:00:00","version":1,"traceId":"t-1",
             "pagoId":77,"alumnoId":1,"matriculaId":%d,"conceptoTipo":"%s","monto":350.00}
            """.formatted(eventId, tipo, matriculaId, concepto);
    }

    @Test
    void pagoConfirmadoDejaPorAprobarUnaSolaVezYLaAprobacionConfirma() {
        MatriculaResponse m = crear(1, 10);
        String id = java.util.UUID.randomUUID().toString();
        pagoConsumer.procesar(pagoJson(id, m.id(), "MATRICULA", "pago.confirmado"), false);
        pagoConsumer.procesar(pagoJson(id, m.id(), "MATRICULA", "pago.confirmado"), false);   // duplicado
        // otro evento distinto para la misma matrícula tampoco la confirma de nuevo
        pagoConsumer.procesar(pagoJson(java.util.UUID.randomUUID().toString(), m.id(), "MATRICULA", "pago.confirmado"), false);

        // el pago NO matricula: queda POR_APROBAR y todavía no se publica matricula.confirmada
        assertThat(jdbc.queryForObject("select estado from matriculas where id = ?", String.class, m.id())).isEqualTo("POR_APROBAR");
        assertThat(eventos("matricula.confirmada")).isZero();
        assertThat(jdbc.queryForObject("select count(*) from matricula_historial where tipo_cambio = 'PAGO'", Integer.class)).isEqualTo(1);

        assertThat(service.aprobar(m.id(), 9L).estado()).isEqualTo("MATRICULADO");
        service.aprobar(m.id(), 9L);   // repetir no publica otra vez
        assertThat(eventos("matricula.confirmada")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from matricula_historial where tipo_cambio = 'APROBACION'", Integer.class)).isEqualTo(1);
    }

    @Test
    void noSeApruebaSinPagoYElRechazoLiberaElCupo() {
        MatriculaResponse sinPago = crear(1, 10);
        assertThatThrownBy(() -> service.aprobar(sinPago.id(), 9L)).isInstanceOf(BusinessRuleException.class).hasMessageContaining("pago");

        service.confirmarPorPago(sinPago.id(), 5L);
        assertThatThrownBy(() -> service.rechazar(sinPago.id(), " ", 9L)).isInstanceOf(BusinessRuleException.class).hasMessageContaining("motivo");
        MatriculaResponse r = service.rechazar(sinPago.id(), "Datos del alumno inconsistentes", 9L);
        assertThat(r.estado()).isEqualTo("ANULADO");
        assertThat(ocupados(10)).isZero();
        assertThat(eventos("matricula.anulada")).isEqualTo(1);
        assertThat(eventos("matricula.confirmada")).isZero();
    }

    @Test
    void pagoQueNoEsDeMatriculaSeIgnora() {
        MatriculaResponse m = crear(1, 10);
        pagoConsumer.procesar(pagoJson(java.util.UUID.randomUUID().toString(), m.id(), "PENSION", "pago.confirmado"), false);
        assertThat(jdbc.queryForObject("select estado from matriculas where id = ?", String.class, m.id())).isEqualTo("PRE_MATRICULA");
        assertThat(eventos("matricula.confirmada")).isZero();
    }

    @Test
    void pagoAnuladoSoloDejaConstanciaYNoRevierteLaMatricula() {
        MatriculaResponse m = crear(1, 10);
        pagoConsumer.procesar(pagoJson(java.util.UUID.randomUUID().toString(), m.id(), "MATRICULA", "pago.confirmado"), false);
        pagoConsumer.procesar(pagoJson(java.util.UUID.randomUUID().toString(), m.id(), "MATRICULA", "pago.anulado"), true);
        assertThat(jdbc.queryForObject("select estado from matriculas where id = ?", String.class, m.id())).isEqualTo("POR_APROBAR");
        assertThat(jdbc.queryForObject("select count(*) from matricula_historial where tipo_cambio = 'ANULACION'", Integer.class)).isEqualTo(1);
    }

    // ------------------------------------------------------------------ traslado, retiro, anulación

    @Test
    void trasladoASeccionLlenaNoCambiaNada() {
        seccion(11L, "B", 1);
        crear(1, 11);                       // B queda llena
        MatriculaResponse m = crear(2, 10);
        assertThatThrownBy(() -> service.trasladar(m.id(), new TrasladoRequest(11L, "Cambio de turno"), 9L))
            .isInstanceOf(BusinessRuleException.class).hasMessageContaining("cupos");
        assertThat(jdbc.queryForObject("select seccion_id from matriculas where id = ?", Long.class, m.id())).isEqualTo(10L);
        assertThat(ocupados(10)).isEqualTo(1);
        assertThat(ocupados(11)).isEqualTo(1);
        assertThat(eventos("matricula.trasladada")).isZero();
    }

    @Test
    void trasladoValidoMueveElCupoYEmiteEvento() {
        seccion(11L, "B", 5);
        MatriculaResponse m = crear(1, 10);
        MatriculaResponse t = service.trasladar(m.id(), new TrasladoRequest(11L, "Pedido del apoderado"), 9L);
        assertThat(t.seccionId()).isEqualTo(11L);
        assertThat(t.estado()).isEqualTo("PRE_MATRICULA");           // el traslado de sección no cambia el estado
        assertThat(ocupados(10)).isZero();
        assertThat(ocupados(11)).isEqualTo(1);
        String json = jdbc.queryForObject("select payload::text from outbox_eventos where topic = 'matricula.trasladada'", String.class);
        assertThat(json).contains("\"seccionAnteriorId\": 10").contains("\"seccionId\": 11");
    }

    @Test
    void retirarExigeMatriculadoYLiberaCupo() {
        MatriculaResponse m = crear(1, 10);
        assertThatThrownBy(() -> service.retirar(m.id(), new RetiroRequest("Mudanza"), 9L))
            .isInstanceOf(BusinessRuleException.class).hasMessageContaining("MATRICULADO");
        service.confirmarPorPago(m.id(), 77L);
        service.aprobar(m.id(), 9L);
        MatriculaResponse r = service.retirar(m.id(), new RetiroRequest("Mudanza"), 9L);
        assertThat(r.estado()).isEqualTo("RETIRADO");
        assertThat(ocupados(10)).isZero();
        assertThat(eventos("matricula.retirada")).isEqualTo(1);
    }

    @Test
    void anularSoloPreMatriculaYLiberaCupo() {
        MatriculaResponse m = crear(1, 10);
        MatriculaResponse a = service.anular(m.id(), "Error de digitación", 9L);
        assertThat(a.estado()).isEqualTo("ANULADO");
        assertThat(ocupados(10)).isZero();
        assertThatThrownBy(() -> service.anular(m.id(), "otra vez", 9L)).isInstanceOf(BusinessRuleException.class);
        // el alumno puede volver a matricularse porque la anulada ya no está "viva"
        assertThat(crear(1, 10).estado()).isEqualTo("PRE_MATRICULA");
    }

    @Test
    void preMatriculaVencidaSeAnulaYLiberaElCupo() {
        MatriculaResponse m = crear(1, 10);
        jdbc.update("update matriculas set vence_en = now() - interval '1 day' where id = ?", m.id());
        assertThat(vencimientoJob.ejecutar()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select estado from matriculas where id = ?", String.class, m.id())).isEqualTo("ANULADO");
        assertThat(ocupados(10)).isZero();
        assertThat(eventos("matricula.anulada")).isEqualTo(1);
        assertThat(vencimientoJob.ejecutar()).isZero();                  // no repite
    }

    // ------------------------------------------------------------------ outbox

    @Test
    void elEventoQuedaEnOutboxAunqueKafkaEsteCaidoYSeEnviaAlVolver() {
        crear(1, 10);
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker caído")));
        outboxPublisher.publicarPendientes();
        assertThat(jdbc.queryForObject("select intentos from outbox_eventos", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select enviado_en from outbox_eventos", java.sql.Timestamp.class)).isNull();
        assertThat(jdbc.queryForObject("select ultimo_error from outbox_eventos", String.class)).contains("broker caído");

        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));
        outboxPublisher.publicarPendientes();
        assertThat(jdbc.queryForObject("select enviado_en from outbox_eventos", java.sql.Timestamp.class)).isNotNull();
        verify(kafka, atLeastOnce()).send(eq("matricula.creada"), anyString(), contains("\"eventType\": \"matricula.creada\""));
    }

    // ------------------------------------------------------------------ ratificación

    @Test
    void ratificacionMasivaReportaLosQueFallanSinDetenerALosDemas() {
        MatriculaResponse a = crear(1, 10);
        MatriculaResponse b = crear(2, 10);
        service.confirmarPorPago(a.id(), 1L);
        service.confirmarPorPago(b.id(), 2L);
        service.aprobar(a.id(), 9L);
        service.aprobar(b.id(), 9L);

        long anioSiguiente = 2L;
        when(planificacion.anio(anioSiguiente)).thenReturn(new PlanificacionClient.Anio(anioSiguiente, "2027", LocalDate.of(2027, 3, 1), LocalDate.of(2027, 12, 20), "PLANIFICADO"));
        when(planificacion.grado(4L)).thenReturn(new PlanificacionClient.Grado(4L, NIVEL, "Primaria", (short) 4, "4° Primaria", true));
        when(planificacion.seccion(20L)).thenReturn(new PlanificacionClient.Seccion(20L, "A", 4L, NIVEL, anioSiguiente, (short) 30, "MAÑANA", null));
        var camp = campanias.crear(new CampaniaRequest(anioSiguiente, "Matrícula 2027", LocalDate.now(), LocalDate.now().plusDays(60)));
        campanias.abrir(camp.id());

        when(pagoClient.deuda(1L)).thenReturn(new PagoClient.Deuda(1L, false, java.math.BigDecimal.ZERO, 0));
        when(pagoClient.deuda(2L)).thenReturn(new PagoClient.Deuda(2L, true, new java.math.BigDecimal("350.00"), 1));

        RatificacionResultado r = ratificacion.ratificar(new RatificacionRequest(anioSiguiente, null, 10L, 20L, null), 9L);

        assertThat(r.solicitadas()).isEqualTo(2);
        assertThat(r.creadas()).isEqualTo(1);
        assertThat(r.errores()).hasSize(1);
        assertThat(r.errores().get(0).alumnoId()).isEqualTo(2L);
        assertThat(r.errores().get(0).motivo()).contains("deuda");
        assertThat(r.matriculas().get(0).tipo()).isEqualTo("RATIFICACION");
        assertThat(r.matriculas().get(0).matriculaAnteriorId()).isEqualTo(a.id());
    }

    @Test
    void listarConYSinFiltroDeTexto() throws Exception {
        crear(1, 10);
        crear(2, 10);
        assertThat(service.listar(null, null, null, null, null, org.springframework.data.domain.PageRequest.of(0, 10)).totalElementos()).isEqualTo(2);
        assertThat(service.listar(ANIO, GRADO, 10L, "PRE_MATRICULA", "alumno 2", org.springframework.data.domain.PageRequest.of(0, 10)).totalElementos()).isEqualTo(1);
        mvc.perform(get("/api/v1/matriculas").with(usuario(5, "SECRETARIA"))).andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElementos").value(2));
        mvc.perform(get("/api/v1/matriculas").param("texto", "MAT-2026-000001").with(usuario(5, "SECRETARIA"))).andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElementos").value(1));
    }

    // ------------------------------------------------------------------ API y seguridad

    JwtRequestPostProcessor usuario(long id, String rol) {
        return jwt().jwt(j -> j.claim("usuarioId", id).claim("realm_access", Map.of("roles", List.of(rol))))
            .authorities(new SimpleGrantedAuthority("ROLE_" + rol));
    }

    @Test
    void soloAdminYSecretariaCreanMatriculas() throws Exception {
        String body = "{\"alumnoId\":1,\"seccionId\":10,\"tipo\":\"NUEVA\"}";
        mvc.perform(post("/api/v1/matriculas").with(usuario(5, "DOCENTE")).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/matriculas").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/matriculas").with(usuario(5, "SECRETARIA")).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.estado").value("PRE_MATRICULA"))
            .andExpect(jsonPath("$.codigo").value("MAT-2026-000001"));
        // validación de entrada
        mvc.perform(post("/api/v1/matriculas").with(usuario(5, "SECRETARIA")).contentType(MediaType.APPLICATION_JSON).content("{\"seccionId\":10}"))
            .andExpect(status().isBadRequest());
        // segundo intento del mismo alumno -> 409
        mvc.perform(post("/api/v1/matriculas").with(usuario(5, "SECRETARIA")).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isConflict());
    }

    @Test
    void elApoderadoSoloVeLasMatriculasDeSusHijosYNoLasAjenas() throws Exception {
        MatriculaResponse suya = crear(1, 10);
        MatriculaResponse ajena = crear(2, 10);
        when(alumnos.hijosDeUsuario(700L)).thenReturn(List.of(new AlumnosClient.Hijo(1L, "A-1", "Alumno 1", "ACTIVO", "PADRE")));

        mvc.perform(get("/api/v1/matriculas/mias").with(usuario(700, "APODERADO")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(suya.id()));
        mvc.perform(get("/api/v1/matriculas/" + suya.id()).with(usuario(700, "APODERADO"))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/matriculas/" + ajena.id()).with(usuario(700, "APODERADO"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/matriculas").with(usuario(700, "APODERADO"))).andExpect(status().isForbidden());
    }

    @Test
    void campaniasLasGestionaSoloAdmin() throws Exception {
        String body = "{\"anioId\":5,\"nombre\":\"Extra\",\"fechaInicio\":\"2026-01-01\",\"fechaFin\":\"2026-02-01\"}";
        mvc.perform(post("/api/v1/matriculas/campanias").with(usuario(5, "SECRETARIA")).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/matriculas/campanias").with(usuario(1, "ADMIN")).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated());
    }

    @Test
    void soloUnaCampaniaAbiertaPorAnio() {
        var otra = campanias.crear(new CampaniaRequest(ANIO, "Segunda vuelta", LocalDate.now(), LocalDate.now().plusDays(10)));
        assertThatThrownBy(() -> campanias.abrir(otra.id())).isInstanceOf(BusinessRuleException.class).hasMessageContaining("ABIERTA");
    }

    @Test
    void internoDevuelveMatriculasVivasDeLaSeccionYLaVigenteDelAlumno() throws Exception {
        MatriculaResponse m = crear(1, 10);
        mvc.perform(interno("/internal/matriculas/seccion/10")).andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].matriculaId").value(m.id()));
        mvc.perform(interno("/internal/matriculas/alumno/1").param("anioId", "1")).andExpect(status().isOk())
            .andExpect(jsonPath("$.seccionId").value(10));
        mvc.perform(interno("/internal/matriculas/alumno/99").param("anioId", "1")).andExpect(status().isNotFound());
    }

    @Test
    void sinCupoDisponibleEnVacantesSeVeLaOcupacion() throws Exception {
        crear(1, 10);
        var s10 = new PlanificacionClient.Seccion(10L, "A", GRADO, NIVEL, ANIO, (short) 2, "MAÑANA", null);
        when(planificacion.secciones(ANIO, GRADO)).thenReturn(List.of(s10));
        mvc.perform(get("/api/v1/matriculas/cupos").param("anioId", "1").param("gradoId", "3").with(usuario(5, "SECRETARIA")))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].capacidad").value(2))
            .andExpect(jsonPath("$[0].ocupados").value(1)).andExpect(jsonPath("$[0].vacantes").value(1));
    }

    @Test
    void conPlanificacionCaidaNoSePuedeMatricular503() throws Exception {
        when(planificacion.seccion(10L)).thenThrow(new pe.edu.upeu.eduspace.matricula.exception.ServicioNoDisponibleException("planificacion-ms caído"));
        mvc.perform(post("/api/v1/matriculas").with(usuario(5, "SECRETARIA")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"alumnoId\":1,\"seccionId\":10}"))
            .andExpect(status().isServiceUnavailable());
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder interno(String url) {
        return get(url).header("X-Service-Token", "test-token");
    }

    @Test
    void internoSinTokenOConTokenErroneoDa401() throws Exception {
        mvc.perform(get("/internal/matriculas/seccion/10")).andExpect(status().isUnauthorized());
        mvc.perform(get("/internal/matriculas/seccion/10").header("X-Service-Token", "otro")).andExpect(status().isUnauthorized());
    }
}
