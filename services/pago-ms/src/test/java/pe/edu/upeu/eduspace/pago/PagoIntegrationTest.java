package pe.edu.upeu.eduspace.pago;

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
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pe.edu.upeu.eduspace.pago.client.AlumnoContactoResponse;
import pe.edu.upeu.eduspace.pago.client.AlumnosGateway;
import pe.edu.upeu.eduspace.pago.client.PlanificacionClient;
import pe.edu.upeu.eduspace.pago.dto.*;
import pe.edu.upeu.eduspace.pago.exception.BusinessRuleException;
import pe.edu.upeu.eduspace.pago.messaging.MatriculaEventConsumer;
import pe.edu.upeu.eduspace.pago.messaging.OutboxPublisher;
import pe.edu.upeu.eduspace.pago.scheduler.VencimientoJob;
import pe.edu.upeu.eduspace.pago.service.*;

@AutoConfigureMockMvc
class PagoIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired MatriculaEventConsumer consumer;
    @Autowired PagoService pagos;
    @Autowired CajaService cajas;
    @Autowired AnulacionService anulaciones;
    @Autowired TarifaService tarifas;
    @Autowired VencimientoJob vencimientoJob;
    @Autowired pe.edu.upeu.eduspace.pago.scheduler.AvisoPreviosJob avisoJob;
    @Autowired OutboxPublisher outboxPublisher;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;

    @MockitoBean PlanificacionClient planificacionClient;
    @MockitoBean AlumnosGateway alumnosGateway;
    @MockitoBean MinioClient minio;
    @MockitoBean KafkaTemplate<String, String> kafka;

    static final long ANIO = 1, NIVEL = 1, GRADO = 3, CAJERO = 20;

    @BeforeEach
    void preparar() {
        jdbc.execute("TRUNCATE alertas_admin, comprobantes, pago_detalle, pagos, cajas_sesion, descuentos_alumno, descuentos, cuotas, tarifas, matriculas_local, "
            + "outbox_eventos, eventos_procesados RESTART IDENTITY CASCADE");
        jdbc.update("update series_comprobante set ultimo_numero = 0");
        reset(planificacionClient, alumnosGateway, minio, kafka);
        when(planificacionClient.getAnio(ANIO)).thenReturn(new PlanificacionClient.AnioInterno(ANIO, "2026",
            LocalDate.of(2026, 3, 1), LocalDate.of(2026, 12, 20), "ACTIVO"));
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));
    }

    // ------------------------------------------------------------------ helpers

    String matriculaJson(String tipo, long matriculaId, long alumnoId) {
        return """
            {"eventId":"%s","eventType":"%s","occurredAt":"2026-02-01T10:00:00","version":1,"traceId":"t",
             "matriculaId":%d,"alumnoId":%d,"alumnoNombre":"Alumno %d","anioId":%d,"nivelId":%d,"gradoId":%d,"seccionId":10,
             "estado":"PRE_MATRICULA","tipo":"NUEVA","responsablePagoUsuarioId":700,"responsablePagoNombre":"Papá %d"}
            """.formatted(UUID.randomUUID(), tipo, matriculaId, alumnoId, alumnoId, ANIO, NIVEL, GRADO, alumnoId);
    }

    long concepto(String codigo) {
        return jdbc.queryForObject("select id from conceptos_pago where codigo = ?", Long.class, codigo);
    }

    void tarifa(String codigo, String monto, int cuotas, int mesInicio) {
        TarifaRequest r = new TarifaRequest();
        r.setAnioId(ANIO); r.setNivelId(NIVEL); r.setGradoId(GRADO); r.setConceptoId(concepto(codigo));
        r.setMonto(new BigDecimal(monto)); r.setNumCuotas((short) cuotas); r.setMesInicio((short) mesInicio); r.setDiaVencimiento((short) 5);
        tarifas.crear(r);
    }

    void matriculaCreada(long matriculaId, long alumnoId) {
        consumer.procesarEventoMatricula(matriculaJson("matricula.creada", matriculaId, alumnoId));
    }

    long cuotaMatricula(long matriculaId) {
        return jdbc.queryForObject("select c.id from cuotas c join conceptos_pago p on p.id = c.concepto_id where c.matricula_id = ? and p.tipo = 'MATRICULA'", Long.class, matriculaId);
    }

    RegistrarPagoRequest pago(long alumnoId, long cuotaId, String monto, String clave) {
        RegistrarPagoRequest r = new RegistrarPagoRequest();
        r.setAlumnoId(alumnoId); r.setPagadorNombre("Papá"); r.setPagadorDocumento("12345678");
        r.setMontoTotal(new BigDecimal(monto)); r.setMedio("EFECTIVO"); r.setIdempotencyKey(clave); r.setTipoComprobante("RECIBO");
        PagoDetalleRequest d = new PagoDetalleRequest();
        d.setCuotaId(cuotaId); d.setMontoAplicado(new BigDecimal(monto));
        r.setDetalles(List.of(d));
        return r;
    }

    void abrirCaja() {
        AbrirCajaRequest r = new AbrirCajaRequest();
        r.setMontoApertura(new BigDecimal("100"));
        cajas.abrirCaja(CAJERO, r);
    }

    int cuenta(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    // ------------------------------------------------------------------ generación de cuotas

    @Test
    void matriculaCreadaGeneraLaCuotaDeMatriculaYElReplayNoDuplica() {
        tarifa("MATRICULA", "350.00", 1, 3);
        String json = matriculaJson("matricula.creada", 1, 1);
        consumer.procesarEventoMatricula(json);
        consumer.procesarEventoMatricula(json);                       // mismo eventId
        assertThat(cuenta("select count(*) from cuotas")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select saldo from cuotas", BigDecimal.class)).isEqualByComparingTo("350.00");
        assertThat(jdbc.queryForObject("select responsable_usuario_id from matriculas_local", Long.class)).isEqualTo(700L);
        // un evento NUEVO de la misma matrícula tampoco duplica (uk_cuota)
        consumer.procesarEventoMatricula(matriculaJson("matricula.creada", 1, 1));
        assertThat(cuenta("select count(*) from cuotas")).isEqualTo(1);
    }

    @Test
    void pensionSeRepartePorElTotalConDiferenciaEnLaUltimaCuotaYFechasConAnioCalendario() {
        tarifa("PENSION", "1000.00", 3, 11);
        matriculaCreada(1, 1);
        consumer.procesarEventoMatricula(matriculaJson("matricula.confirmada", 1, 1));
        List<Map<String, Object>> cuotas = jdbc.queryForList("select numero_cuota, monto_base, fecha_vencimiento, descripcion from cuotas "
            + "where concepto_id = ? order by numero_cuota", concepto("PENSION"));
        assertThat(cuotas).hasSize(3);
        assertThat((BigDecimal) cuotas.get(0).get("monto_base")).isEqualByComparingTo("333.33");
        assertThat((BigDecimal) cuotas.get(1).get("monto_base")).isEqualByComparingTo("333.33");
        assertThat((BigDecimal) cuotas.get(2).get("monto_base")).isEqualByComparingTo("333.34");
        assertThat(cuotas.get(0).get("fecha_vencimiento").toString()).isEqualTo("2026-11-05");
        assertThat(cuotas.get(1).get("fecha_vencimiento").toString()).isEqualTo("2026-12-05");
        assertThat(cuotas.get(2).get("fecha_vencimiento").toString()).isEqualTo("2027-01-05");   // cruza de año
        assertThat(jdbc.queryForObject("select sum(monto_base) from cuotas where concepto_id = ?", BigDecimal.class, concepto("PENSION")))
            .isEqualByComparingTo("1000.00");
    }

    @Test
    void sinPlanificacionElAnioDeLasCuotasEsElActualYNuncaElAnioUno() {
        when(planificacionClient.getAnio(ANIO)).thenThrow(new RuntimeException("planificacion-ms caído"));
        tarifa("PENSION", "300.00", 2, 3);
        matriculaCreada(1, 1);
        consumer.procesarEventoMatricula(matriculaJson("matricula.confirmada", 1, 1));
        assertThat(jdbc.queryForObject("select min(extract(year from fecha_vencimiento))::int from cuotas", Integer.class))
            .isEqualTo(LocalDate.now().getYear());
    }

    @Test
    void sinTarifaSeCreaUnaAlertaYAlConfigurarlaSeGeneranLasCuotas() {
        matriculaCreada(1, 1);
        assertThat(cuenta("select count(*) from cuotas")).isZero();
        assertThat(jdbc.queryForMap("select tipo, resuelta from alertas_admin")).containsEntry("tipo", "SIN_TARIFA").containsEntry("resuelta", false);
        matriculaCreada(1, 1);                                          // no repite la alerta
        assertThat(cuenta("select count(*) from alertas_admin")).isEqualTo(1);

        tarifa("MATRICULA", "350.00", 1, 3);                            // el admin configura la tarifa
        assertThat(cuenta("select count(*) from cuotas")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select resuelta from alertas_admin", Boolean.class)).isTrue();
    }

    // ------------------------------------------------------------------ cobro

    @Test
    void cobroPresencialExigeCajaAbierta() {
        tarifa("MATRICULA", "350.00", 1, 3);
        matriculaCreada(1, 1);
        assertThatThrownBy(() -> pagos.registrarPago(pago(1, cuotaMatricula(1), "350.00", "k-1"), CAJERO))
            .isInstanceOf(BusinessRuleException.class).hasMessageContaining("caja");
    }

    @Test
    void pagoEmiteUnSoloComprobantePdfYEventoPlanoDePagoConfirmado() throws Exception {
        tarifa("MATRICULA", "350.00", 1, 3);
        matriculaCreada(1, 1);
        abrirCaja();
        PagoResponse p = pagos.registrarPago(pago(1, cuotaMatricula(1), "350.00", "k-1"), CAJERO);

        assertThat(p.getEstado()).isEqualTo("CONFIRMADO");
        assertThat(p.getComprobante().getNumeroCompleto()).isEqualTo("R001-00000001");
        assertThat(p.getComprobante().getRutaPdf()).endsWith(".pdf");
        assertThat(jdbc.queryForObject("select estado from cuotas", String.class)).isEqualTo("PAGADA");
        assertThat(jdbc.queryForObject("select saldo from cuotas", BigDecimal.class)).isEqualByComparingTo("0");

        ArgumentCaptor<PutObjectArgs> subida = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(minio).putObject(subida.capture());
        assertThat(subida.getValue().contentType()).isEqualTo("application/pdf");
        assertThat(subida.getValue().stream().readNBytes(5)).isEqualTo("%PDF-".getBytes());

        String json = jdbc.queryForObject("select payload::text from outbox_eventos where topic = 'pago.confirmado'", String.class);
        assertThat(json).contains("\"conceptoTipo\": \"MATRICULA\"").contains("\"matriculaId\": 1").contains("\"alumnoId\": 1")
            .contains("\"eventType\": \"pago.confirmado\"").contains("\"eventId\"").doesNotContain("\"payload\"");
    }

    @Test
    void consultarUnPagoNuncaEmiteComprobantesNiConsumeCorrelativos() throws Exception {
        tarifa("MATRICULA", "350.00", 1, 3);
        matriculaCreada(1, 1);
        abrirCaja();
        PagoResponse p = pagos.registrarPago(pago(1, cuotaMatricula(1), "350.00", "k-1"), CAJERO);

        for (int i = 0; i < 3; i++) {
            PagoResponse leido = pagos.obtenerPorId(p.getId());
            assertThat(leido.getComprobante()).isNotNull();
            assertThat(leido.getComprobante().getNumeroCompleto()).isEqualTo("R001-00000001");
        }
        pagos.listarCobranza(null, null, null, org.springframework.data.domain.PageRequest.of(0, 10));
        PagoResponse replay = pagos.registrarPago(pago(1, cuotaMatricula(1), "350.00", "k-1"), CAJERO);   // misma clave
        assertThat(replay.getId()).isEqualTo(p.getId());

        assertThat(cuenta("select count(*) from comprobantes")).isEqualTo(1);
        assertThat(cuenta("select count(*) from pagos")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select ultimo_numero from series_comprobante where tipo = 'RECIBO'", Long.class)).isEqualTo(1L);
        verify(minio, times(1)).putObject(any(PutObjectArgs.class));
    }

    @Test
    void numerosDeComprobanteCorrelativosSinSaltos() {
        tarifa("PENSION", "300.00", 3, 3);
        matriculaCreada(1, 1);
        consumer.procesarEventoMatricula(matriculaJson("matricula.confirmada", 1, 1));
        abrirCaja();
        List<Long> ids = jdbc.queryForList("select id from cuotas order by numero_cuota", Long.class);
        for (int i = 0; i < ids.size(); i++) pagos.registrarPago(pago(1, ids.get(i), "100.00", "k-" + i), CAJERO);
        assertThat(jdbc.queryForList("select numero from comprobantes order by numero", Long.class)).containsExactly(1L, 2L, 3L);
    }

    @Test
    void montoMayorAlSaldoOCuotaYaPagadaSeRechaza() {
        tarifa("MATRICULA", "350.00", 1, 3);
        matriculaCreada(1, 1);
        abrirCaja();
        long cuota = cuotaMatricula(1);
        RegistrarPagoRequest excede = pago(1, cuota, "400.00", "k-1");
        assertThatThrownBy(() -> pagos.registrarPago(excede, CAJERO)).isInstanceOf(BusinessRuleException.class).hasMessageContaining("supera");
        RegistrarPagoRequest descuadre = pago(1, cuota, "350.00", "k-2");
        descuadre.setMontoTotal(new BigDecimal("340.00"));
        assertThatThrownBy(() -> pagos.registrarPago(descuadre, CAJERO)).isInstanceOf(BusinessRuleException.class).hasMessageContaining("no coincide");
        pagos.registrarPago(pago(1, cuota, "350.00", "k-3"), CAJERO);
        assertThatThrownBy(() -> pagos.registrarPago(pago(1, cuota, "10.00", "k-4"), CAJERO)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void pagoParcialDejaLaCuotaEnParcial() {
        tarifa("MATRICULA", "350.00", 1, 3);
        matriculaCreada(1, 1);
        abrirCaja();
        PagoResponse p = pagos.registrarPago(pago(1, cuotaMatricula(1), "100.00", "k-1"), CAJERO);
        assertThat(p.getDetalles().get(0).getNuevoEstadoCuota()).isEqualTo("PARCIAL");
        assertThat(jdbc.queryForObject("select saldo from cuotas", BigDecimal.class)).isEqualByComparingTo("250.00");
    }

    @Test
    void dosCobrosSimultaneosDeLaMismaCuotaSoloUnoPasa() throws Exception {
        tarifa("MATRICULA", "350.00", 1, 3);
        matriculaCreada(1, 1);
        abrirCaja();
        long cuota = cuotaMatricula(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch salida = new CountDownLatch(1);
        List<Future<Object>> res = List.of(
            pool.submit(() -> { salida.await(); return intentar(pago(1, cuota, "350.00", "k-a")); }),
            pool.submit(() -> { salida.await(); return intentar(pago(1, cuota, "350.00", "k-b")); }));
        salida.countDown();
        int ok = 0;
        for (Future<Object> f : res) if (f.get(30, TimeUnit.SECONDS) instanceof PagoResponse) ok++;
        pool.shutdown();
        assertThat(ok).isEqualTo(1);
        assertThat(cuenta("select count(*) from pagos where estado = 'CONFIRMADO'")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select saldo from cuotas", BigDecimal.class)).isEqualByComparingTo("0");
        assertThat(cuenta("select count(*) from comprobantes")).isEqualTo(1);
    }

    Object intentar(RegistrarPagoRequest r) {
        try {
            return pagos.registrarPago(r, CAJERO);
        } catch (Exception e) {
            return e;
        }
    }

    // ------------------------------------------------------------------ anulación

    @Test
    void anularPagoRevierteElSaldoAnulaElComprobanteYEmiteEventoConConcepto() {
        tarifa("MATRICULA", "350.00", 1, 3);
        matriculaCreada(1, 1);
        abrirCaja();
        PagoResponse p = pagos.registrarPago(pago(1, cuotaMatricula(1), "350.00", "k-1"), CAJERO);
        AnularPagoRequest req = new AnularPagoRequest();
        req.setMotivo("Cobro duplicado");
        anulaciones.anularPago(p.getId(), req, 1L);

        assertThat(jdbc.queryForObject("select estado from cuotas", String.class)).isEqualTo("PENDIENTE");
        assertThat(jdbc.queryForObject("select saldo from cuotas", BigDecimal.class)).isEqualByComparingTo("350.00");
        assertThat(jdbc.queryForObject("select estado from comprobantes", String.class)).isEqualTo("ANULADO");
        String json = jdbc.queryForObject("select payload::text from outbox_eventos where topic = 'pago.anulado'", String.class);
        assertThat(json).contains("\"conceptoTipo\": \"MATRICULA\"").contains("\"matriculaId\": 1");
        assertThatThrownBy(() -> anulaciones.anularPago(p.getId(), req, 1L)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void noSeAnulaUnPagoDeUnaCajaCerrada() {
        tarifa("MATRICULA", "350.00", 1, 3);
        matriculaCreada(1, 1);
        abrirCaja();
        PagoResponse p = pagos.registrarPago(pago(1, cuotaMatricula(1), "350.00", "k-1"), CAJERO);
        CerrarCajaRequest cierre = new CerrarCajaRequest();
        cierre.setMontoCierreDeclarado(new BigDecimal("450.00"));
        cajas.cerrarCaja(CAJERO, cierre);
        AnularPagoRequest req = new AnularPagoRequest();
        req.setMotivo("Error");
        assertThatThrownBy(() -> anulaciones.anularPago(p.getId(), req, 1L)).isInstanceOf(BusinessRuleException.class).hasMessageContaining("cerrada");
    }

    @Test
    void retiroAnulaLasCuotasPendientesYAvisaDeLasParciales() {
        tarifa("PENSION", "300.00", 3, 3);
        tarifa("MATRICULA", "350.00", 1, 3);
        matriculaCreada(1, 1);
        consumer.procesarEventoMatricula(matriculaJson("matricula.confirmada", 1, 1));
        abrirCaja();
        List<Long> pensiones = jdbc.queryForList("select c.id from cuotas c join conceptos_pago p on p.id = c.concepto_id where p.tipo = 'PENSION' order by numero_cuota", Long.class);
        pagos.registrarPago(pago(1, pensiones.get(0), "100.00", "k-1"), CAJERO);          // pagada completa
        pagos.registrarPago(pago(1, pensiones.get(1), "40.00", "k-2"), CAJERO);           // parcial
        consumer.procesarEventoMatricula(matriculaJson("matricula.retirada", 1, 1));

        assertThat(jdbc.queryForList("select estado from cuotas order by id")).extracting(m -> m.get("estado"))
            .containsExactly("ANULADA", "PAGADA", "PARCIAL", "ANULADA");     // matrícula pendiente, p1 pagada, p2 parcial, p3 pendiente
        assertThat(jdbc.queryForObject("select tipo from alertas_admin", String.class)).isEqualTo("CUOTA_PARCIAL");
    }

    // ------------------------------------------------------------------ vencimiento y mora

    @Test
    void cuotaVencidaPublicaEventoYLaMoraNoSeCapitalizaDiaADia() {
        tarifa("MATRICULA", "350.00", 1, 3);
        matriculaCreada(1, 1);
        jdbc.update("update cuotas set fecha_vencimiento = current_date - 30");
        vencimientoJob.ejecutar();
        assertThat(jdbc.queryForObject("select estado from cuotas", String.class)).isEqualTo("VENCIDA");
        BigDecimal mora1 = jdbc.queryForObject("select mora from cuotas", BigDecimal.class);
        assertThat(mora1).isEqualByComparingTo("7.00");                       // 350 × 2 %/mes × 30 días
        String json = jdbc.queryForObject("select payload::text from outbox_eventos where topic = 'pago.vencido'", String.class);
        assertThat(json).contains("\"cuotaId\"").contains("\"alumnoId\": 1").contains("\"eventId\"");

        vencimientoJob.ejecutar();                              // mismo día: la mora no cambia ni se repite el evento
        assertThat(jdbc.queryForObject("select mora from cuotas", BigDecimal.class)).isEqualByComparingTo(mora1);
        assertThat(cuenta("select count(*) from outbox_eventos where topic = 'pago.vencido'")).isEqualTo(1);

        jdbc.update("update cuotas set fecha_vencimiento = current_date - 60");   // pasan 30 días más
        vencimientoJob.ejecutar();
        assertThat(jdbc.queryForObject("select mora from cuotas", BigDecimal.class)).isEqualByComparingTo("14.00");   // lineal, sin interés compuesto
        assertThat(jdbc.queryForObject("select saldo from cuotas", BigDecimal.class)).isEqualByComparingTo("364.00");
    }

    @Test
    void avisoPrevioSeEnviaUnaSolaVezAntesDelVencimiento() {
        tarifa("MATRICULA", "350.00", 1, 3);
        matriculaCreada(1, 1);
        jdbc.update("update cuotas set fecha_vencimiento = current_date + 2");
        avisoJob.ejecutar();
        avisoJob.ejecutar();
        assertThat(cuenta("select count(*) from outbox_eventos where topic = 'pago.por-vencer'")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select aviso_enviado from cuotas", Boolean.class)).isTrue();
    }

    @Test
    void outboxPublicaYMarcaEnviado() {
        tarifa("MATRICULA", "350.00", 1, 3);
        matriculaCreada(1, 1);
        abrirCaja();
        pagos.registrarPago(pago(1, cuotaMatricula(1), "350.00", "k-1"), CAJERO);
        outboxPublisher.publicarEventosPendientes();
        verify(kafka).send(eq("pago.confirmado"), anyString(), contains("\"eventType\": \"pago.confirmado\""));
        assertThat(cuenta("select count(*) from outbox_eventos where enviado_en is null")).isZero();
    }

    // ------------------------------------------------------------------ API

    JwtRequestPostProcessor usuario(long id, String rol) {
        return jwt().jwt(j -> j.claim("usuarioId", id).claim("realm_access", Map.of("roles", List.of(rol))))
            .authorities(new SimpleGrantedAuthority("ROLE_" + rol));
    }

    @Test
    void soloElCajeroCobraYSoloConUsuarioIdenToken() throws Exception {
        tarifa("MATRICULA", "350.00", 1, 3);
        matriculaCreada(1, 1);
        long cuota = cuotaMatricula(1);
        String body = """
            {"alumnoId":1,"pagadorNombre":"Papá","montoTotal":350.00,"medio":"EFECTIVO","idempotencyKey":"api-1",
             "detalles":[{"cuotaId":%d,"montoAplicado":350.00}]}
            """.formatted(cuota);
        mvc.perform(post("/api/v1/pagos").with(usuario(5, "DOCENTE")).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        // CAJERO sin caja abierta -> 409
        mvc.perform(post("/api/v1/pagos").with(usuario(CAJERO, "CAJERO")).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isConflict());
        mvc.perform(post("/api/v1/pagos/caja/abrir").with(usuario(CAJERO, "CAJERO")).contentType(MediaType.APPLICATION_JSON).content("{\"montoApertura\":50}"))
            .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/pagos").with(usuario(CAJERO, "CAJERO")).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.estado").value("CONFIRMADO"))
            .andExpect(jsonPath("$.comprobante.numeroCompleto").value("R001-00000001"));
        // leer el pago dos veces no cambia nada
        Long pagoId = jdbc.queryForObject("select id from pagos", Long.class);
        mvc.perform(get("/api/v1/pagos/" + pagoId).with(usuario(CAJERO, "CAJERO"))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/pagos/" + pagoId).with(usuario(CAJERO, "CAJERO"))).andExpect(status().isOk());
        assertThat(cuenta("select count(*) from comprobantes")).isEqualTo(1);
    }

    @Test
    void apoderadoVeLasCuotasDeSusHijosYNoLasAjenas() throws Exception {
        tarifa("MATRICULA", "350.00", 1, 3);
        matriculaCreada(1, 1);
        matriculaCreada(2, 2);
        when(alumnosGateway.hijos(700L)).thenReturn(List.of(new AlumnoContactoResponse.HijoResumen(1L, "A-1", "Alumno 1", "ACTIVO", "PADRE")));
        mvc.perform(get("/api/v1/pagos/mias/cuotas").with(usuario(700, "APODERADO")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].alumnoId").value(1));
        mvc.perform(get("/api/v1/pagos/mias/cuotas").with(usuario(5, "DOCENTE"))).andExpect(status().isForbidden());
    }

    @Test
    void pagoEnLineaSoloParaCuotasDeLosPropiosHijos() throws Exception {
        tarifa("MATRICULA", "350.00", 1, 3);
        matriculaCreada(1, 1);
        matriculaCreada(2, 2);
        when(alumnosGateway.hijos(700L)).thenReturn(List.of(new AlumnoContactoResponse.HijoResumen(1L, "A-1", "Alumno 1", "ACTIVO", "PADRE")));
        long cuotaPropia = jdbc.queryForObject("select id from cuotas where alumno_id = 1", Long.class);
        long cuotaAjena = jdbc.queryForObject("select id from cuotas where alumno_id = 2", Long.class);
        java.util.function.BiFunction<Long, Long, String> pago = (alumno, cuota) -> "{\"alumnoId\":" + alumno + ",\"montoTotal\":350.00,\"medio\":\"ONLINE_SIMULADO\","
            + "\"idempotencyKey\":\"k-" + alumno + "-" + cuota + "\",\"detalles\":[{\"cuotaId\":" + cuota + ",\"montoAplicado\":350.00}]}";

        // un alumno ajeno, o una cuota ajena declarada bajo un hijo propio, se rechazan
        mvc.perform(post("/api/v1/pagos/online").with(usuario(700, "APODERADO")).contentType(MediaType.APPLICATION_JSON).content(pago.apply(2L, cuotaAjena)))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/pagos/online").with(usuario(700, "APODERADO")).contentType(MediaType.APPLICATION_JSON).content(pago.apply(1L, cuotaAjena)))
            .andExpect(status().isForbidden());
        // la cuota del propio hijo se paga y queda registrada
        mvc.perform(post("/api/v1/pagos/online").with(usuario(700, "APODERADO")).contentType(MediaType.APPLICATION_JSON).content(pago.apply(1L, cuotaPropia)))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.medio").value("ONLINE_SIMULADO"))
            .andExpect(jsonPath("$.comprobante.numeroCompleto").isNotEmpty());
        org.junit.jupiter.api.Assertions.assertEquals("PAGADA", jdbc.queryForObject("select estado from cuotas where id = ?", String.class, cuotaPropia));
        org.junit.jupiter.api.Assertions.assertEquals("PENDIENTE", jdbc.queryForObject("select estado from cuotas where id = ?", String.class, cuotaAjena));
    }

    @Test
    void listarCuotasFiltraPorAlumnoEstadoYSeccion() throws Exception {
        tarifa("MATRICULA", "350.00", 1, 3);
        matriculaCreada(1, 1);
        matriculaCreada(2, 2);
        mvc.perform(get("/api/v1/pagos/cuotas").with(usuario(8, "SECRETARIA"))).andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElementos").value(2));
        mvc.perform(get("/api/v1/pagos/cuotas").param("alumnoId", "2").with(usuario(8, "SECRETARIA"))).andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElementos").value(1)).andExpect(jsonPath("$.contenido[0].alumnoId").value(2));
        mvc.perform(get("/api/v1/pagos/cuotas").param("alumnoId", "2").param("estado", "PAGADA").with(usuario(8, "SECRETARIA")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(0));
    }

    @Test
    void internoInformaDeudaVencidaParaLaRatificacion() throws Exception {
        tarifa("MATRICULA", "350.00", 1, 3);
        matriculaCreada(1, 1);
        mvc.perform(interno("/internal/pagos/alumno/1/deuda")).andExpect(status().isOk()).andExpect(jsonPath("$.tieneDeudaVencida").value(false));
        jdbc.update("update cuotas set fecha_vencimiento = current_date - 5, estado = 'VENCIDA'");
        mvc.perform(interno("/internal/pagos/alumno/1/deuda")).andExpect(status().isOk())
            .andExpect(jsonPath("$.tieneDeudaVencida").value(true)).andExpect(jsonPath("$.montoVencido").value(350.0));
    }

    @Test
    void descuentosSeEditanConPutYSoloElAdmin() throws Exception {
        String crear = "{\"nombre\":\"Hermanos\",\"tipo\":\"PORCENTAJE\",\"valor\":10,\"aplicaA\":\"PENSION\"}";
        mvc.perform(post("/api/v1/pagos/descuentos").with(usuario(1, "ADMIN")).contentType(MediaType.APPLICATION_JSON).content(crear)).andExpect(status().isCreated());
        Long id = jdbc.queryForObject("select id from descuentos", Long.class);
        String editar = "{\"nombre\":\"Hermanos\",\"tipo\":\"PORCENTAJE\",\"valor\":15,\"aplicaA\":\"PENSION\"}";
        mvc.perform(put("/api/v1/pagos/descuentos/" + id).with(usuario(8, "SECRETARIA")).contentType(MediaType.APPLICATION_JSON).content(editar)).andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/pagos/descuentos/" + id).with(usuario(1, "ADMIN")).contentType(MediaType.APPLICATION_JSON).content(editar))
            .andExpect(status().isOk()).andExpect(jsonPath("$.valor").value(15));
    }

    @Test
    void descuentoSeAplicaPorCuotaAlGenerarLasPensiones() {
        jdbc.update("insert into descuentos (nombre, tipo, valor, aplica_a, activo) values ('Hermanos','PORCENTAJE',10,'PENSION',true)");
        tarifa("PENSION", "300.00", 3, 3);
        matriculaCreada(1, 1);   // crea matricula_local (sin tarifa de matrícula: solo alerta)
        jdbc.update("insert into descuentos_alumno (alumno_id, descuento_id, anio_id, motivo, vigente) values (1, 1, ?, 'Hermanos', true)", ANIO);
        consumer.procesarEventoMatricula(matriculaJson("matricula.confirmada", 1, 1));
        assertThat(jdbc.queryForObject("select sum(descuento) from cuotas where concepto_id = ?", BigDecimal.class, concepto("PENSION"))).isEqualByComparingTo("30.00");
        assertThat(jdbc.queryForObject("select sum(saldo) from cuotas where concepto_id = ?", BigDecimal.class, concepto("PENSION"))).isEqualByComparingTo("270.00");
    }

    @Test
    void desactivarTarifaConMontoRedondoNoLoTomaPorCambioDeMonto() {
        tarifa("PENSION", "150.00", 10, 3);
        long id = jdbc.queryForObject("select id from tarifas where monto = 150", Long.class);
        TarifaRequest r = new TarifaRequest();
        r.setAnioId(ANIO); r.setNivelId(NIVEL); r.setGradoId(GRADO); r.setConceptoId(concepto("PENSION"));
        r.setMonto(new BigDecimal("150")); r.setNumCuotas((short) 10); r.setMesInicio((short) 3); r.setDiaVencimiento((short) 5);
        r.setActivo(false);

        assertThat(tarifas.actualizar(id, r).getActivo()).isFalse();   // 150 y 150.00 son el mismo monto

        r.setMonto(new BigDecimal("160"));
        assertThatThrownBy(() -> tarifas.actualizar(id, r)).isInstanceOf(BusinessRuleException.class);
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder interno(String url) {
        return get(url).header("X-Service-Token", "test-token");
    }

    @Test
    void internoSinTokenOConTokenErroneoDa401() throws Exception {
        mvc.perform(get("/internal/pagos/alumno/1/deuda")).andExpect(status().isUnauthorized());
        mvc.perform(get("/internal/pagos/alumno/1/deuda").header("X-Service-Token", "otro")).andExpect(status().isUnauthorized());
    }
}
