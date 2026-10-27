package pe.edu.upeu.eduspace.matricula.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.upeu.eduspace.matricula.config.KafkaConfig;
import pe.edu.upeu.eduspace.matricula.entity.EventoProcesado;
import pe.edu.upeu.eduspace.matricula.entity.MatriculaHistorial;
import pe.edu.upeu.eduspace.matricula.event.EventoSobre;
import pe.edu.upeu.eduspace.matricula.event.PagoConfirmadoEvent;
import pe.edu.upeu.eduspace.matricula.exception.ResourceNotFoundException;
import pe.edu.upeu.eduspace.matricula.repository.EventoProcesadoRepository;
import pe.edu.upeu.eduspace.matricula.repository.HistorialRepository;
import pe.edu.upeu.eduspace.matricula.service.MatriculaService;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;

/**
 * pago.confirmado -> confirma la matrícula (solo concepto MATRICULA). pago.anulado -> deja constancia en el
 * historial; la reversión es una decisión manual de secretaría.
 */
@Slf4j
@Component
public class PagoEventConsumer {

    private static final int REINTENTOS_MATRICULA_NO_VISIBLE = 5;

    private final EventoProcesadoRepository eventoRepo;
    private final HistorialRepository historialRepo;
    private final MatriculaService matriculaService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate tx;

    public PagoEventConsumer(EventoProcesadoRepository eventoRepo, HistorialRepository historialRepo,
                             MatriculaService matriculaService, ObjectMapper objectMapper,
                             PlatformTransactionManager txManager) {
        this.eventoRepo = eventoRepo;
        this.historialRepo = historialRepo;
        this.matriculaService = matriculaService;
        this.objectMapper = objectMapper;
        this.tx = new TransactionTemplate(txManager);
    }

    @KafkaListener(topics = KafkaConfig.TOPIC_PAGO_CONFIRMADO, groupId = "matricula-ms")
    public void pagoConfirmado(String json) {
        procesar(json, false);
    }

    @KafkaListener(topics = KafkaConfig.TOPIC_PAGO_ANULADO, groupId = "matricula-ms")
    public void pagoAnulado(String json) {
        procesar(json, true);
    }

    /** Público para pruebas: procesa un mensaje con el sobre estándar. */
    public void procesar(String json, boolean anulado) {
        EventoSobre<PagoConfirmadoEvent> sobre;
        try {
            sobre = EventoSobre.leer(objectMapper, json, PagoConfirmadoEvent.class);
        } catch (Exception e) {
            log.error("pago.* mal formado descartado: {}", e.getMessage());
            return;
        }
        PagoConfirmadoEvent ev = sobre.getPayload();
        if (sobre.getTraceId() != null) MDC.put("traceId", sobre.getTraceId());
        try {
            if (sobre.getEventId() == null) {
                log.warn("Evento pago.* sin eventId descartado");
                return;
            }
            if (!"MATRICULA".equalsIgnoreCase(ev.getConceptoTipo()) || ev.getMatriculaId() == null) {
                log.debug("Evento {} ignorado (concepto {}).", sobre.getEventId(), ev.getConceptoTipo());
                return;
            }
            if (eventoRepo.existsByEventId(sobre.getEventId())) {
                log.info("Evento {} ya procesado; se ignora.", sobre.getEventId());
                return;
            }
            procesarConReintento(sobre, ev, anulado);
        } finally {
            MDC.remove("traceId");
        }
    }

    private void procesarConReintento(EventoSobre<PagoConfirmadoEvent> sobre, PagoConfirmadoEvent ev, boolean anulado) {
        ResourceNotFoundException ultimo = null;
        for (int i = 1; i <= REINTENTOS_MATRICULA_NO_VISIBLE; i++) {
            try {
                tx.executeWithoutResult(status -> {
                    if (anulado) {
                        historialRepo.save(MatriculaHistorial.builder()
                            .matriculaId(ev.getMatriculaId()).tipoCambio("ANULACION")
                            .motivo("Pago de matrícula " + ev.getPagoId() + " anulado en pago-ms; requiere decisión de secretaría.").build());
                        log.warn("ALERTA SECRETARÍA: pago {} de la matrícula {} fue anulado.", ev.getPagoId(), ev.getMatriculaId());
                    } else {
                        matriculaService.confirmarPorPago(ev.getMatriculaId(), ev.getPagoId());
                    }
                    eventoRepo.save(EventoProcesado.builder()
                        .eventId(sobre.getEventId()).eventType(sobre.getEventType() != null ? sobre.getEventType() : "pago.*").build());
                });
                return;
            } catch (ResourceNotFoundException e) {
                ultimo = e;
                log.warn("Matrícula {} aún no visible (intento {}/{}).", ev.getMatriculaId(), i, REINTENTOS_MATRICULA_NO_VISIBLE);
                try { Thread.sleep(500L * i); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); return; }
            }
        }
        // Agotados los reintentos: se lanza para que el contenedor lo derive al manejo de errores (DLT/reintento del broker)
        throw new IllegalStateException("Matrícula " + ev.getMatriculaId() + " no existe tras reintentos", ultimo);
    }
}
