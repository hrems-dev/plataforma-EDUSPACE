package pe.edu.upeu.eduspace.examenes.messaging;

import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import pe.edu.upeu.eduspace.examenes.config.KafkaConfig;
import pe.edu.upeu.eduspace.examenes.event.EventoSobre;
import pe.edu.upeu.eduspace.examenes.event.ExamenCalificadoEvent;
import pe.edu.upeu.eduspace.examenes.event.ExamenPublicadoEvent;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExamenEventProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final pe.edu.upeu.eduspace.examenes.client.PlanificacionClient planificacionClient;
    private final ObjectMapper objectMapper;

    public void publicarExamenPublicado(ExamenPublicadoEvent event) {
        try {
            UUID eventId = UUID.randomUUID();
            String traceId = MDC.get("traceId");

            EventoSobre<ExamenPublicadoEvent> sobre = EventoSobre.<ExamenPublicadoEvent>builder()
                .eventId(eventId)
                .eventType(KafkaConfig.TOPIC_EXAMEN_PUBLICADO)
                .occurredAt(LocalDateTime.now())
                .version(1)
                .traceId(traceId != null ? traceId : UUID.randomUUID().toString())
                .payload(event)
                .build();

            String json = EventoSobre.escribir(objectMapper, sobre);
            kafkaTemplate.send(KafkaConfig.TOPIC_EXAMEN_PUBLICADO, String.valueOf(event.getExamenId()), json);
            log.info("Evento examen.publicado emitido para examen ID: {}", event.getExamenId());
        } catch (Exception e) {
            log.error("Error al emitir evento examen.publicado: {}", e.getMessage(), e);
        }
    }

    public void publicarExamenCalificado(ExamenCalificadoEvent event) {
        if (event.getCursoNombre() == null) {
            try {
                var curso = planificacionClient.getCursoPorId(event.getCursoId());
                if (curso != null && curso.get("nombre") != null) event.setCursoNombre(curso.get("nombre").toString());
            } catch (Exception ignored) {
                // el nombre es solo informativo para la notificación
            }
        }
        try {
            UUID eventId = UUID.randomUUID();
            String traceId = MDC.get("traceId");

            EventoSobre<ExamenCalificadoEvent> sobre = EventoSobre.<ExamenCalificadoEvent>builder()
                .eventId(eventId)
                .eventType(KafkaConfig.TOPIC_EXAMEN_CALIFICADO)
                .occurredAt(LocalDateTime.now())
                .version(1)
                .traceId(traceId != null ? traceId : UUID.randomUUID().toString())
                .payload(event)
                .build();

            String json = EventoSobre.escribir(objectMapper, sobre);
            kafkaTemplate.send(KafkaConfig.TOPIC_EXAMEN_CALIFICADO, String.valueOf(event.getExamenId()), json);
            log.info("Evento examen.calificado emitido para alumno {} en examen {} con nota {}",
                event.getAlumnoId(), event.getExamenId(), event.getNota());
        } catch (Exception e) {
            log.error("Error al emitir evento examen.calificado: {}", e.getMessage(), e);
        }
    }
}

