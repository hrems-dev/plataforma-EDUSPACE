package pe.edu.upeu.eduspace.calificaciones.messaging;

import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upeu.eduspace.calificaciones.config.KafkaConfig;
import pe.edu.upeu.eduspace.calificaciones.event.EventoSobre;
import pe.edu.upeu.eduspace.calificaciones.event.TareaCalificadaEvent;
import pe.edu.upeu.eduspace.calificaciones.service.NotaEventoService;

@Slf4j
@Component
@RequiredArgsConstructor
public class TareaCalificadaConsumer {

    private final NotaEventoService notaEventoService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaConfig.TOPIC_TAREA_CALIFICADA, groupId = "calificaciones-ms")
    @Transactional
    public void procesarTareaCalificada(String mensajeJson) {
        try {
            EventoSobre<TareaCalificadaEvent> sobre = EventoSobre.leer(objectMapper, mensajeJson, TareaCalificadaEvent.class);

            if (sobre.getTraceId() != null) {
                MDC.put("traceId", sobre.getTraceId());
            }

            log.info("Recibido evento tarea.calificada: eventId={}, tareaId={}, nota={}",
                sobre.getEventId(), sobre.getPayload().getTareaId(), sobre.getPayload().getNota());

            notaEventoService.procesarTareaCalificada(sobre.getEventId(), sobre.getPayload());
        } catch (Exception e) {
            log.error("Error al procesar evento tarea.calificada: {}", e.getMessage(), e);
        } finally {
            MDC.remove("traceId");
        }
    }
}

