package pe.edu.eduspace.notificaciones.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import pe.edu.eduspace.notificaciones.client.AlumnosGateway;
import pe.edu.eduspace.notificaciones.messaging.NotificacionEventConsumer;
import pe.edu.eduspace.notificaciones.service.ContactosAlumnoService;
import pe.edu.eduspace.notificaciones.service.EmailSender;
import pe.edu.eduspace.notificaciones.service.ReintentoService;

/** El flujo completo: evento de Kafka → destinatarios (alumnos-ms simulado) → notificaciones por canal → reintentos. */
class NotificacionesFlujoIntegrationTest extends PostgresIntegrationTestBase {

    static final AlumnosStub ALUMNOS = new AlumnosStub();

    // Familia del alumno 1: el alumno (301), el padre principal (300) y la madre responsable de pago (310)
    static final String PADRE = AlumnosStub.apoderado(10, 300L, "Ramos, Carlos", "carlos@mail.pe", true, false);
    static final String MADRE = AlumnosStub.apoderado(11, 310L, "Díaz, María", "maria@mail.pe", false, true);

    @DynamicPropertySource
    static void alumnosMs(DynamicPropertyRegistry registry) {
        registry.add("app.clients.alumnos.url", ALUMNOS::url);
    }

    @AfterAll
    static void apagar() {
        // el servidor simulado se cierra con la JVM de pruebas
    }

    @MockitoBean EmailSender emailSender;
    @Autowired NotificacionEventConsumer consumer;
    @Autowired ReintentoService reintento;
    @Autowired ContactosAlumnoService contactos;
    @Autowired CircuitBreakerRegistry breakers;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void limpiar() {
        jdbc.update("delete from intentos_envio");
        jdbc.update("delete from notificaciones");
        jdbc.update("delete from eventos_procesados");
        jdbc.update("delete from preferencias_notificacion");
        reset(emailSender);
        ALUMNOS.reiniciar();
        contactos.vaciarCache();
        breakers.circuitBreaker("alumnosClient").reset();
        ALUMNOS.alumno(1, "Ramos Pérez, Luis", 301L, PADRE, MADRE);
        ALUMNOS.alumno(2, "Ramos Pérez, Ana", 302L, PADRE);
    }

    // ---------- utilidades ----------

    void publicar(String topico, String json) {
        // el método de escucha se invoca como lo haría el contenedor de Kafka
        var registro = new ConsumerRecord<>(topico, 0, 0L, "clave", json);
        switch (topico) {
            case "matricula.creada" -> consumer.matriculaCreada(registro);
            case "matricula.confirmada" -> consumer.matriculaConfirmada(registro);
            case "matricula.anulada" -> consumer.matriculaAnulada(registro);
            case "pago.confirmado" -> consumer.pagoConfirmado(registro);
            case "pago.vencido" -> consumer.pagoVencido(registro);
            case "pago.por-vencer" -> consumer.pagoPorVencer(registro);
            case "asistencia.falta-registrada" -> consumer.faltaRegistrada(registro);
            case "tarea.publicada" -> consumer.tareaPublicada(registro);
            case "tarea.recordatorio" -> consumer.tareaRecordatorio(registro);
            case "tarea.calificada" -> consumer.tareaCalificada(registro);
            case "examen.publicado" -> consumer.examenPublicado(registro);
            case "examen.calificado" -> consumer.examenCalificado(registro);
            case "boletin.generado" -> consumer.boletinGenerado(registro);
            case "horario.publicado" -> consumer.horarioPublicado(registro);
            case "justificacion.resuelta" -> consumer.justificacionResuelta(registro);
            default -> throw new IllegalArgumentException(topico);
        }
    }

    static String id() {
        return "\"eventId\":\"" + UUID.randomUUID() + "\"";
    }

    List<Map<String, Object>> bandeja(long usuarioId) {
        return jdbc.queryForList("select tipo, titulo, mensaje, estado from notificaciones where usuario_id = ? and canal = 'IN_APP' order by id", usuarioId);
    }

    long total(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    // ---------- cada evento llega a los destinatarios correctos ----------

    static Stream<Arguments> eventos() {
        String todos = "301,300,310";
        return Stream.of(
                Arguments.of("matricula.creada", "{" + id() + ",\"alumnoId\":1,\"matriculaId\":50,\"anio\":\"2026\",\"gradoNombre\":\"1° de Primaria\",\"seccionNombre\":\"A\"}",
                        "MATRICULA_CREADA", todos, "La matrícula de Ramos Pérez, Luis para el año 2026 fue registrada."),
                Arguments.of("matricula.confirmada", "{" + id() + ",\"alumnoId\":1,\"matriculaId\":50,\"seccionNombre\":\"A\"}",
                        "MATRICULA_CONFIRMADA", todos, "La matrícula de Ramos Pérez, Luis fue confirmada. Sección: A."),
                Arguments.of("matricula.anulada", "{" + id() + ",\"alumnoId\":1,\"matriculaId\":50,\"motivo\":\"Venció el plazo de pago\"}",
                        "MATRICULA_ANULADA", "310", "La pre-matrícula de Ramos Pérez, Luis fue anulada: Venció el plazo de pago."),
                Arguments.of("pago.confirmado", "{" + id() + ",\"alumnoId\":1,\"pagoId\":9,\"monto\":150,\"concepto\":\"Matrícula 2026\"}",
                        "PAGO_CONFIRMADO", "310", "Recibimos tu pago de S/ 150.00 por Matrícula 2026."),
                Arguments.of("pago.vencido", "{" + id() + ",\"alumnoId\":1,\"cuotaId\":7,\"concepto\":\"Pensión marzo\",\"fechaVencimiento\":\"2026-03-31\"}",
                        "PAGO_VENCIDO", "310", "La cuota \"Pensión marzo\" de Ramos Pérez, Luis venció el 31/03/2026."),
                Arguments.of("pago.por-vencer", "{" + id() + ",\"alumnoId\":1,\"cuotaId\":8,\"concepto\":\"Pensión abril\",\"saldo\":300.5,\"fechaVencimiento\":[2026,4,30]}",
                        "PAGO_POR_VENCER", "310", "La cuota \"Pensión abril\" de Ramos Pérez, Luis vence el 30/04/2026 (S/ 300.50)."),
                Arguments.of("asistencia.falta-registrada", "{" + id() + ",\"alumnoId\":1,\"asistenciaDetalleId\":33,\"fecha\":\"2026-05-04\"}",
                        "ASISTENCIA_FALTA", todos, "Ramos Pérez, Luis tiene falta registrada el 04/05/2026."),
                Arguments.of("tarea.calificada", "{" + id() + ",\"alumnoId\":1,\"tareaId\":4,\"entregaId\":21,\"titulo\":\"Fracciones\",\"nota\":17.50}",
                        "TAREA_CALIFICADA", todos, "Tu tarea \"Fracciones\" fue calificada: 17.5."),
                Arguments.of("examen.calificado", "{" + id() + ",\"alumnoId\":1,\"examenId\":6,\"intentoId\":61,\"cursoNombre\":\"Matemática\",\"nota\":18}",
                        "EXAMEN_CALIFICADO", todos, "Ya está disponible la nota de Matemática: 18."),
                Arguments.of("boletin.generado", "{" + id() + ",\"alumnoId\":1,\"boletinId\":12,\"periodoNombre\":\"I Bimestre\"}",
                        "BOLETIN_GENERADO", todos, "El boletín de I Bimestre de Ramos Pérez, Luis ya está disponible."),
                Arguments.of("justificacion.resuelta", "{" + id() + ",\"alumnoId\":1,\"justificacionId\":3,\"resultado\":\"APROBADA\"}",
                        "JUSTIFICACION_RESUELTA", "300,310", "La justificación de Ramos Pérez, Luis fue aprobada."));
    }

    @ParameterizedTest(name = "{0} → {2} para {3}")
    @MethodSource("eventos")
    void cadaEventoGeneraLaNotificacionCorrectaParaLosDestinatariosCorrectos(String topico, String json, String plantilla,
                                                                            String destinatarios, String mensaje) {
        publicar(topico, json);

        List<Long> esperados = Stream.of(destinatarios.split(",")).map(Long::valueOf).toList();
        for (long usuario : List.of(301L, 300L, 310L)) {
            if (esperados.contains(usuario)) {
                assertThat(bandeja(usuario)).as("bandeja de %d", usuario).hasSize(1);
                assertThat(bandeja(usuario).get(0)).containsEntry("tipo", plantilla).containsEntry("mensaje", mensaje)
                        .containsEntry("estado", "ENVIADA");
            } else {
                assertThat(bandeja(usuario)).as("bandeja de %d", usuario).isEmpty();
            }
        }
        assertThat(total("select count(*) from notificaciones where usuario_id = 0")).isZero();
    }

    @Test
    void recordatorioDeTareaAvisaSoloALosQueNoEntregaronYNoSeDuplica() {
        String cuerpo = "{" + id() + ",\"tareaId\":4,\"cursoNombre\":\"Matemática\",\"titulo\":\"Fracciones\",\"fechaEntrega\":\"2026-06-10T23:59:00\",\"alumnoIds\":[2]}";
        publicar("tarea.recordatorio", cuerpo);
        assertThat(bandeja(302)).hasSize(1);
        assertThat(bandeja(302).get(0)).containsEntry("mensaje", "Recordatorio: la tarea de Matemática \"Fracciones\" vence el 10/06/2026 y aún no tiene entrega.");
        assertThat(bandeja(301)).isEmpty();                       // el alumno 1 ya entregó: no está en la lista
        publicar("tarea.recordatorio", cuerpo);                    // mismo eventId
        assertThat(bandeja(302)).hasSize(1);
        publicar("tarea.recordatorio", "{\"tareaId\":9,\"alumnoIds\":[]}");   // sin alumnos: se descarta
        assertThat(bandeja(302)).hasSize(1);
    }

    @Test
    void tareaYExamenPublicadosResuelvenCadaAlumnoDeLaLista() {
        publicar("tarea.publicada", "{" + id() + ",\"tareaId\":4,\"cursoNombre\":\"Matemática\",\"titulo\":\"Fracciones\",\"fechaEntrega\":\"2026-06-10T23:59:00\",\"alumnoIds\":[1,2]}");
        // el padre es apoderado de ambos hijos pero recibe un solo aviso (misma tarea): lo evita la restricción única
        assertThat(bandeja(301)).hasSize(1);
        assertThat(bandeja(302)).hasSize(1);
        assertThat(bandeja(300)).hasSize(1);
        assertThat(bandeja(310)).hasSize(1);
        assertThat(bandeja(301).get(0)).containsEntry("mensaje", "Nueva tarea de Matemática: Fracciones. Vence el 10/06/2026.");

        publicar("examen.publicado", "{" + id() + ",\"examenId\":8,\"cursoNombre\":\"Comunicación\",\"titulo\":\"Examen bimestral\",\"fechaInicio\":\"2026-06-20\",\"alumnoIds\":[2]}");
        assertThat(bandeja(302).get(1)).containsEntry("mensaje", "Examen de Comunicación: Examen bimestral. Disponible del 20/06/2026.");
        assertThat(bandeja(310)).hasSize(1);   // la madre no es apoderada del alumno 2
    }

    @Test
    void horarioPublicadoAvisaSoloALosDocentesSinConsultarAAlumnosMs() {
        publicar("horario.publicado", "{" + id() + ",\"seccionId\":5,\"seccionNombre\":\"1° de Primaria A\",\"versionHorario\":2,\"version\":1,\"docenteUsuarioIds\":[77,78]}");
        assertThat(bandeja(77)).hasSize(1);
        assertThat(bandeja(77).get(0)).containsEntry("mensaje", "Se publicó el horario de 1° de Primaria A (versión 2). Revísalo en la agenda.");
        assertThat(bandeja(78)).hasSize(1);
        assertThat(ALUMNOS.llamadas()).isZero();
    }

    // ---------- correo ----------

    @Test
    void elPagoConfirmadoTambienSalePorCorreoSoloAlResponsableDePago() {
        publicar("pago.confirmado", "{" + id() + ",\"alumnoId\":1,\"pagoId\":9,\"monto\":150,\"concepto\":\"Matrícula 2026\"}");

        verify(emailSender).enviar("maria@mail.pe", "Pago confirmado — EduSpace", "Hemos recibido el pago de S/ 150.00 por Matrícula 2026. Gracias.");
        verify(emailSender, times(1)).enviar(any(), any(), any());
        assertThat(jdbc.queryForObject("select estado from notificaciones where canal = 'EMAIL'", String.class)).isEqualTo("ENVIADA");
        assertThat(total("select count(*) from intentos_envio where exitoso")).isEqualTo(1);
    }

    @Test
    void siElCorreoFallaQuedaFallidaYElReintentoLaEntrega() {
        doThrow(new MailSendException("SMTP caído")).when(emailSender).enviar(any(), any(), any());
        publicar("pago.confirmado", "{" + id() + ",\"alumnoId\":1,\"pagoId\":9,\"monto\":150,\"concepto\":\"Matrícula 2026\"}");

        assertThat(jdbc.queryForObject("select estado from notificaciones where canal = 'EMAIL'", String.class)).isEqualTo("FALLIDA");
        assertThat(bandeja(310)).hasSize(1);   // la bandeja de la app no depende del correo
        reset(emailSender);
        assertThat(reintento.procesarPendientes()).isEqualTo(1);

        verify(emailSender).enviar(eq("maria@mail.pe"), any(), any());
        assertThat(jdbc.queryForObject("select estado from notificaciones where canal = 'EMAIL'", String.class)).isEqualTo("ENVIADA");
        assertThat(jdbc.queryForList("select numero_intento, exitoso from intentos_envio order by numero_intento"))
                .extracting(f -> f.get("exitoso")).containsExactly(false, true);
    }

    @Test
    void trasTresFallosQuedaFallidaParaSiempre() {
        doThrow(new MailSendException("SMTP caído")).when(emailSender).enviar(any(), any(), any());
        publicar("pago.confirmado", "{" + id() + ",\"alumnoId\":1,\"pagoId\":9,\"monto\":150,\"concepto\":\"Matrícula 2026\"}");   // intento 1
        assertThat(reintento.procesarPendientes()).isEqualTo(1);   // intento 2
        assertThat(reintento.procesarPendientes()).isEqualTo(1);   // intento 3
        assertThat(reintento.procesarPendientes()).isZero();       // ya no se reintenta

        assertThat(jdbc.queryForObject("select estado from notificaciones where canal = 'EMAIL'", String.class)).isEqualTo("FALLIDA");
        assertThat(total("select count(*) from intentos_envio")).isEqualTo(3);
    }

    // ---------- anti-duplicado e idempotencia ----------

    @Test
    void elMismoEventoEntregadoDosVecesGeneraUnaSolaNotificacion() {
        String evento = "{" + id() + ",\"alumnoId\":1,\"pagoId\":9,\"monto\":150,\"concepto\":\"Matrícula 2026\"}";
        publicar("pago.confirmado", evento);
        publicar("pago.confirmado", evento);

        assertThat(bandeja(310)).hasSize(1);
        assertThat(total("select count(*) from notificaciones")).isEqualTo(2);   // IN_APP + EMAIL de la madre
        assertThat(total("select count(*) from eventos_procesados")).isEqualTo(1);
        verify(emailSender, times(1)).enviar(any(), any(), any());
    }

    @Test
    void sinEventIdLaRestriccionUnicaDeLaBaseEvitaElDuplicado() {
        String sinSobre = "{\"alumnoId\":1,\"pagoId\":9,\"monto\":150,\"concepto\":\"Matrícula 2026\"}";
        publicar("pago.confirmado", sinSobre);
        publicar("pago.confirmado", sinSobre);
        assertThat(bandeja(310)).hasSize(1);
        assertThat(total("select count(*) from eventos_procesados")).isZero();
    }

    // ---------- preferencias ----------

    @Test
    void unaPreferenciaDeshabilitadaNoCreaLaNotificacionDeEseCanal() {
        jdbc.update("insert into preferencias_notificacion (usuario_id, tipo, canal, habilitada) values (310, 'PAGO_CONFIRMADO', 'EMAIL', false)");
        publicar("pago.confirmado", "{" + id() + ",\"alumnoId\":1,\"pagoId\":9,\"monto\":150,\"concepto\":\"Matrícula 2026\"}");

        assertThat(bandeja(310)).hasSize(1);
        assertThat(total("select count(*) from notificaciones where canal = 'EMAIL'")).isZero();
        verify(emailSender, never()).enviar(any(), any(), any());

        jdbc.update("insert into preferencias_notificacion (usuario_id, tipo, canal, habilitada) values (310, 'PAGO_VENCIDO', 'IN_APP', false)");
        publicar("pago.vencido", "{" + id() + ",\"alumnoId\":1,\"cuotaId\":7,\"concepto\":\"Pensión\",\"fechaVencimiento\":\"2026-03-31\"}");
        assertThat(bandeja(310)).hasSize(1);   // la de PAGO_CONFIRMADO; la de PAGO_VENCIDO no se creó
    }

    // ---------- alumnos-ms caído ----------

    @Test
    void conAlumnosMsCaidoLaNotificacionQuedaPendienteYElReintentoLaResuelve() {
        ALUMNOS.caido(true);
        publicar("pago.confirmado", "{" + id() + ",\"alumnoId\":1,\"pagoId\":9,\"monto\":150,\"concepto\":\"Matrícula 2026\"}");

        assertThat(bandeja(310)).isEmpty();
        assertThat(jdbc.queryForList("select usuario_id, estado, tipo from notificaciones"))
                .singleElement().satisfies(f -> {
                    assertThat(f).containsEntry("usuario_id", 0L).containsEntry("estado", "PENDIENTE").containsEntry("tipo", "PAGO_CONFIRMADO");
                });

        // sigue caído: el intento falla pero la notificación no se pierde
        assertThat(reintento.procesarPendientes()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select estado from notificaciones where usuario_id = 0", String.class)).isEqualTo("PENDIENTE");
        assertThat(bandeja(310)).isEmpty();

        // alumnos-ms vuelve: el siguiente ciclo crea las notificaciones reales y cierra la pendiente
        ALUMNOS.caido(false);
        breakers.circuitBreaker("alumnosClient").reset();
        assertThat(reintento.procesarPendientes()).isEqualTo(1);

        assertThat(bandeja(310)).hasSize(1);
        assertThat(bandeja(310).get(0)).containsEntry("mensaje", "Recibimos tu pago de S/ 150.00 por Matrícula 2026.");
        assertThat(jdbc.queryForObject("select estado from notificaciones where usuario_id = 0", String.class)).isEqualTo("ENVIADA");
        verify(emailSender).enviar(eq("maria@mail.pe"), any(), any());
        assertThat(jdbc.queryForList("select exitoso from intentos_envio where notificacion_id = (select id from notificaciones where usuario_id = 0) order by numero_intento"))
                .extracting(f -> f.get("exitoso")).containsExactly(false, true);
    }

    @Test
    void trasRepetirseLasFallasElCircuitoSeAbreYNoSeLlamaMas() {
        ALUMNOS.caido(true);
        for (int i = 1; i <= 4; i++) {
            publicar("boletin.generado", "{" + id() + ",\"alumnoId\":1,\"boletinId\":" + i + ",\"periodoNombre\":\"I\"}");
        }
        assertThat(breakers.circuitBreaker("alumnosClient").getState()).isEqualTo(CircuitBreaker.State.OPEN);
        int llamadas = ALUMNOS.llamadas();

        publicar("boletin.generado", "{" + id() + ",\"alumnoId\":1,\"boletinId\":99,\"periodoNombre\":\"I\"}");
        assertThat(ALUMNOS.llamadas()).as("con el circuito abierto ya no se golpea a alumnos-ms").isEqualTo(llamadas);
        assertThat(total("select count(*) from notificaciones where usuario_id = 0 and estado = 'PENDIENTE'")).isEqualTo(5);
    }

    @Test
    void conLosContactosEnCacheUnaCaidaNoAfecta() {
        publicar("boletin.generado", "{" + id() + ",\"alumnoId\":1,\"boletinId\":1,\"periodoNombre\":\"I\"}");
        ALUMNOS.caido(true);
        int llamadas = ALUMNOS.llamadas();

        publicar("boletin.generado", "{" + id() + ",\"alumnoId\":1,\"boletinId\":2,\"periodoNombre\":\"II\"}");

        assertThat(ALUMNOS.llamadas()).isEqualTo(llamadas);
        assertThat(bandeja(310)).hasSize(2);
        assertThat(total("select count(*) from notificaciones where usuario_id = 0")).isZero();
    }

    @Test
    void unAlumnoInexistenteSeDescartaSinAbrirElCircuito() {
        for (int i = 1; i <= 5; i++) {
            publicar("boletin.generado", "{" + id() + ",\"alumnoId\":999,\"boletinId\":" + i + ",\"periodoNombre\":\"I\"}");
        }
        assertThat(total("select count(*) from notificaciones")).isZero();
        assertThat(breakers.circuitBreaker("alumnosClient").getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    // ---------- eventos que no se pueden procesar ----------

    @Test
    void unEventoMalFormadoOSinDatosImprescindiblesSeDescartaSinBloquearLaCola() {
        publicar("pago.confirmado", "esto no es json");
        publicar("pago.confirmado", "{\"pagoId\":9}");                      // sin alumnoId
        publicar("tarea.publicada", "{\"tareaId\":1,\"alumnoIds\":[]}");    // sin alumnos
        publicar("horario.publicado", "{\"seccionId\":1,\"docenteUsuarioIds\":[]}");
        assertThat(total("select count(*) from notificaciones")).isZero();
        assertThat(total("select count(*) from eventos_procesados")).isZero();
    }

    // ---------- limpieza ----------

    @Test
    void laLimpiezaBorraLosEventosProcesadosConMasDeTreintaDias() {
        jdbc.update("insert into eventos_procesados (event_id, event_type, procesado_en) values (?, 'x', now() - interval '31 days')", UUID.randomUUID());
        jdbc.update("insert into eventos_procesados (event_id, event_type, procesado_en) values (?, 'x', now() - interval '5 days')", UUID.randomUUID());
        assertThat(reintento.limpiarEventosProcesados()).isEqualTo(1);
        assertThat(total("select count(*) from eventos_procesados")).isEqualTo(1);
    }
}
