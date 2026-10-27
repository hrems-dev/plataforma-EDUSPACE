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
import pe.edu.upeu.eduspace.calificaciones.event.ExamenCalificadoEvent;
import pe.edu.upeu.eduspace.calificaciones.service.NotaEventoService;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExamenCalificadoConsumer {

    private final NotaEventoService notaEventoService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaConfig.TOPIC_EXAMEN_CALIFICADO, groupId = "calificaciones-ms")
    @Transactional
    public void procesarExamenCalificado(String mensajeJson) {
        try {
            EventoSobre<ExamenCalificadoEvent> sobre = EventoSobre.leer(objectMapper, mensajeJson, ExamenCalificadoEvent.class);

            if (sobre.getTraceId() != null) {
                MDC.put("traceId", sobre.getTraceId());
            }

            log.info("Recibido evento examen.calificado: eventId={}, examenId={}, nota={}",
                sobre.getEventId(), sobre.getPayload().getExamenId(), sobre.getPayload().getNota());

            notaEventoService.procesarExamenCalificado(sobre.getEventId(), sobre.getPayload());
        } catch (Exception e) {
            log.error("Error al procesar evento examen.calificado: {}", e.getMessage(), e);
        } finally {
            MDC.remove("traceId");
        }
    }
}

