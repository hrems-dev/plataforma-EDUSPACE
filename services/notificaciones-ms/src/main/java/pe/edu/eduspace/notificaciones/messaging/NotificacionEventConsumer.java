package pe.edu.eduspace.notificaciones.messaging;

import java.util.List;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import pe.edu.eduspace.notificaciones.event.*;
import pe.edu.eduspace.notificaciones.service.Destino;
import pe.edu.eduspace.notificaciones.service.EventoNotificable;
import pe.edu.eduspace.notificaciones.service.NotificacionOrquestador;
import pe.edu.eduspace.notificaciones.util.Datos;
import tools.jackson.databind.ObjectMapper;

/**
 * Un método de escucha por tópico. Cada uno: registra el evento en el log, decide plantilla y destinatarios, y
 * delega en el orquestador. Un evento mal formado se descarta con log (no bloquea la cola); un fallo de proceso
 * (por ejemplo, la base caída) se propaga para que el manejador de errores lo reintente.
 */
@Component
public class NotificacionEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificacionEventConsumer.class);

    private final NotificacionOrquestador orquestador;
    private final ObjectMapper objectMapper;

    public NotificacionEventConsumer(NotificacionOrquestador orquestador, ObjectMapper objectMapper) {
        this.orquestador = orquestador;
        this.objectMapper = objectMapper;
    }

    // ---------------- matrícula ----------------

    @KafkaListener(topics = Topicos.MATRICULA_CREADA)
    public void matriculaCreada(ConsumerRecord<String, String> r) {
        matricula(r, "MATRICULA_CREADA", Destino.ALUMNO_Y_APODERADOS);
    }

    @KafkaListener(topics = Topicos.MATRICULA_CONFIRMADA)
    public void matriculaConfirmada(ConsumerRecord<String, String> r) {
        matricula(r, "MATRICULA_CONFIRMADA", Destino.ALUMNO_Y_APODERADOS);
    }

    @KafkaListener(topics = Topicos.MATRICULA_ANULADA)
    public void matriculaAnulada(ConsumerRecord<String, String> r) {
        matricula(r, "MATRICULA_ANULADA", Destino.RESPONSABLE_PAGO);
    }

    private void matricula(ConsumerRecord<String, String> r, String plantilla, Destino destino) {
        MatriculaEvent e = leer(r, MatriculaEvent.class);
        if (e == null || requerido(r, "alumnoId", e.alumnoId())) {
            return;
        }
        procesar(r, e.eventId(), clave(r, e.matriculaId()), plantilla, destino, List.of(e.alumnoId()),
                Datos.de("alumno", e.alumnoNombre(), "anio", e.anio() != null ? e.anio() : e.anioId(),
                        "grado", e.gradoNombre(), "seccion", e.seccionNombre(), "motivo", e.motivo()));
    }

    // ---------------- pagos ----------------

    @KafkaListener(topics = Topicos.PAGO_CONFIRMADO)
    public void pagoConfirmado(ConsumerRecord<String, String> r) {
        PagoConfirmadoEvent e = leer(r, PagoConfirmadoEvent.class);
        if (e == null || requerido(r, "alumnoId", e.alumnoId())) {
            return;
        }
        procesar(r, e.eventId(), clave(r, e.pagoId()), "PAGO_CONFIRMADO", Destino.RESPONSABLE_PAGO, List.of(e.alumnoId()),
                Datos.de("monto", Datos.dinero(e.monto()), "concepto", e.concepto()));
    }

    @KafkaListener(topics = Topicos.PAGO_VENCIDO)
    public void pagoVencido(ConsumerRecord<String, String> r) {
        cuota(r, "PAGO_VENCIDO");
    }

    @KafkaListener(topics = Topicos.PAGO_POR_VENCER)
    public void pagoPorVencer(ConsumerRecord<String, String> r) {
        cuota(r, "PAGO_POR_VENCER");
    }

    private void cuota(ConsumerRecord<String, String> r, String plantilla) {
        PagoVencidoEvent e = leer(r, PagoVencidoEvent.class);
        if (e == null || requerido(r, "alumnoId", e.alumnoId())) {
            return;
        }
        procesar(r, e.eventId(), clave(r, e.cuotaId()), plantilla, Destino.RESPONSABLE_PAGO, List.of(e.alumnoId()),
                Datos.de("concepto", e.concepto(), "fecha", Datos.fecha(e.fechaVencimiento()),
                        "monto", Datos.dinero(e.saldo() != null ? e.saldo() : e.monto())));
    }

    // ---------------- asistencia ----------------

    @KafkaListener(topics = Topicos.ASISTENCIA_FALTA)
    public void faltaRegistrada(ConsumerRecord<String, String> r) {
        AsistenciaFaltaRegistradaEvent e = leer(r, AsistenciaFaltaRegistradaEvent.class);
        if (e == null || requerido(r, "alumnoId", e.alumnoId())) {
            return;
        }
        Long id = e.asistenciaDetalleId() != null ? e.asistenciaDetalleId() : e.asistenciaId();
        procesar(r, e.eventId(), clave(r, id), "ASISTENCIA_FALTA", Destino.ALUMNO_Y_APODERADOS, List.of(e.alumnoId()),
                Datos.de("fecha", Datos.fecha(e.fecha())));
    }

    // ---------------- tareas ----------------

    @KafkaListener(topics = Topicos.TAREA_PUBLICADA)
    public void tareaPublicada(ConsumerRecord<String, String> r) {
        TareaPublicadaEvent e = leer(r, TareaPublicadaEvent.class);
        if (e == null || requerido(r, "alumnoIds", e.alumnoIds())) {
            return;
        }
        procesar(r, e.eventId(), clave(r, e.tareaId()), "TAREA_PUBLICADA", Destino.ALUMNO_Y_APODERADOS, e.alumnoIds(),
                Datos.de("curso", e.cursoNombre(), "titulo", e.titulo(), "fecha", Datos.fecha(e.fechaEntrega())));
    }

    /** tareas-ms lo publica una vez por tarea, con los alumnos que aún no entregaron (vence en menos de 24 h). */
    @KafkaListener(topics = Topicos.TAREA_RECORDATORIO)
    public void tareaRecordatorio(ConsumerRecord<String, String> r) {
        TareaPublicadaEvent e = leer(r, TareaPublicadaEvent.class);
        if (e == null || requerido(r, "alumnoIds", e.alumnoIds())) {
            return;
        }
        procesar(r, e.eventId(), clave(r, e.tareaId()), "TAREA_RECORDATORIO", Destino.ALUMNO_Y_APODERADOS, e.alumnoIds(),
                Datos.de("curso", e.cursoNombre(), "titulo", e.titulo(), "fecha", Datos.fecha(e.fechaEntrega())));
    }

    @KafkaListener(topics = Topicos.TAREA_CALIFICADA)
    public void tareaCalificada(ConsumerRecord<String, String> r) {
        TareaCalificadaEvent e = leer(r, TareaCalificadaEvent.class);
        if (e == null || requerido(r, "alumnoId", e.alumnoId())) {
            return;
        }
        String clave = e.entregaId() != null ? String.valueOf(e.entregaId()) : e.tareaId() + ":" + e.alumnoId();
        procesar(r, e.eventId(), clave, "TAREA_CALIFICADA", Destino.ALUMNO_Y_APODERADOS, List.of(e.alumnoId()),
                Datos.de("titulo", e.titulo(), "nota", Datos.nota(e.nota())));
    }

    // ---------------- exámenes ----------------

    @KafkaListener(topics = Topicos.EXAMEN_PUBLICADO)
    public void examenPublicado(ConsumerRecord<String, String> r) {
        ExamenPublicadoEvent e = leer(r, ExamenPublicadoEvent.class);
        if (e == null || requerido(r, "alumnoIds", e.alumnoIds())) {
            return;
        }
        procesar(r, e.eventId(), clave(r, e.examenId()), "EXAMEN_PUBLICADO", Destino.ALUMNO_Y_APODERADOS, e.alumnoIds(),
                Datos.de("curso", e.cursoNombre(), "titulo", e.titulo(), "fecha", Datos.fecha(e.fechaInicio())));
    }

    @KafkaListener(topics = Topicos.EXAMEN_CALIFICADO)
    public void examenCalificado(ConsumerRecord<String, String> r) {
        ExamenCalificadoEvent e = leer(r, ExamenCalificadoEvent.class);
        if (e == null || requerido(r, "alumnoId", e.alumnoId())) {
            return;
        }
        String clave = e.intentoId() != null ? String.valueOf(e.intentoId()) : e.examenId() + ":" + e.alumnoId();
        procesar(r, e.eventId(), clave, "EXAMEN_CALIFICADO", Destino.ALUMNO_Y_APODERADOS, List.of(e.alumnoId()),
                Datos.de("curso", e.cursoNombre(), "titulo", e.titulo(), "nota", Datos.nota(e.nota())));
    }

    // ---------------- boletín, horario, justificaciones ----------------

    @KafkaListener(topics = Topicos.BOLETIN_GENERADO)
    public void boletinGenerado(ConsumerRecord<String, String> r) {
        BoletinGeneradoEvent e = leer(r, BoletinGeneradoEvent.class);
        if (e == null || requerido(r, "alumnoId", e.alumnoId())) {
            return;
        }
        procesar(r, e.eventId(), clave(r, e.boletinId()), "BOLETIN_GENERADO", Destino.ALUMNO_Y_APODERADOS, List.of(e.alumnoId()),
                Datos.de("periodo", e.periodoNombre()));
    }

    /** Avisa solo a docentes: los {@code docenteUsuarioIds} ya vienen en el evento, sin consultar a alumnos-ms. */
    @KafkaListener(topics = Topicos.HORARIO_PUBLICADO)
    public void horarioPublicado(ConsumerRecord<String, String> r) {
        HorarioPublicadoEvent e = leer(r, HorarioPublicadoEvent.class);
        if (e == null || requerido(r, "docenteUsuarioIds", e.docenteUsuarioIds())) {
            return;
        }
        String clave = e.seccionId() + ":v" + e.versionHorario();
        log.info("component=NotificacionEventConsumer eventType={} key={} status=RECIBIDO", r.topic(), clave);
        orquestador.procesar(new EventoNotificable(e.eventId(), r.topic(), clave, "HORARIO_PUBLICADO",
                Destino.USUARIOS_DIRECTOS, List.of(), e.docenteUsuarioIds(),
                Datos.de("seccion", e.seccionNombre(), "version", e.versionHorario())));
    }

    @KafkaListener(topics = Topicos.JUSTIFICACION_RESUELTA)
    public void justificacionResuelta(ConsumerRecord<String, String> r) {
        JustificacionResueltaEvent e = leer(r, JustificacionResueltaEvent.class);
        if (e == null || requerido(r, "alumnoId", e.alumnoId())) {
            return;
        }
        procesar(r, e.eventId(), clave(r, e.justificacionId()), "JUSTIFICACION_RESUELTA", Destino.APODERADOS,
                List.of(e.alumnoId()), Datos.de("resultado", e.resultado() == null ? null : e.resultado().toLowerCase()));
    }

    // ---------------- comunes ----------------

    private void procesar(ConsumerRecord<String, String> r, java.util.UUID eventId, String clave, String plantilla,
                          Destino destino, List<Long> alumnoIds, java.util.Map<String, String> datos) {
        log.info("component=NotificacionEventConsumer eventType={} key={} status=RECIBIDO", r.topic(), clave);
        orquestador.procesar(new EventoNotificable(eventId, r.topic(), clave, plantilla, destino, alumnoIds, List.of(), datos));
    }

    /** Clave de deduplicación: el id de la entidad del evento; si falta, la clave del mensaje de Kafka. */
    private String clave(ConsumerRecord<String, String> r, Long id) {
        return id != null ? String.valueOf(id) : r.key();
    }

    /** Un evento sin el dato imprescindible se descarta con log en lugar de bloquear la cola. */
    private boolean requerido(ConsumerRecord<String, String> r, String campo, Object valor) {
        boolean falta = valor == null || (valor instanceof List<?> l && l.isEmpty());
        if (falta) {
            log.warn("component=NotificacionEventConsumer eventType={} key={} status=DESCARTADO cause=FALTA_{}", r.topic(), r.key(), campo);
        }
        return falta;
    }

    private <T> T leer(ConsumerRecord<String, String> r, Class<T> tipo) {
        try {
            return objectMapper.readValue(r.value(), tipo);
        } catch (RuntimeException ex) {
            log.warn("component=NotificacionEventConsumer eventType={} key={} status=DESCARTADO cause=EVENTO_MAL_FORMADO {}",
                    r.topic(), r.key(), ex.getClass().getSimpleName());
            return null;
        }
    }
}
