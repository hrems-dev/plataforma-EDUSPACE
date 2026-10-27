package pe.edu.upeu.eduspace.calificaciones.messaging;

import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upeu.eduspace.calificaciones.config.KafkaConfig;
import pe.edu.upeu.eduspace.calificaciones.entity.EventoProcesado;
import pe.edu.upeu.eduspace.calificaciones.event.EventoSobre;
import pe.edu.upeu.eduspace.calificaciones.event.MatriculaEvent;
import pe.edu.upeu.eduspace.calificaciones.repository.EventoProcesadoRepository;
import pe.edu.upeu.eduspace.calificaciones.service.MatriculaLocalService;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class MatriculaEventConsumer {

    private final EventoProcesadoRepository eventoProcesadoRepository;
    private final MatriculaLocalService matriculaLocalService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = {
        KafkaConfig.TOPIC_MATRICULA_CREADA,
        KafkaConfig.TOPIC_MATRICULA_CONFIRMADA,
        KafkaConfig.TOPIC_MATRICULA_TRASLADADA,
        KafkaConfig.TOPIC_MATRICULA_RETIRADA,
        KafkaConfig.TOPIC_MATRICULA_ANULADA
    }, groupId = "calificaciones-ms")
    @Transactional
    public void procesarEventoMatricula(String mensajeJson) {
        try {
            EventoSobre<MatriculaEvent> sobre = EventoSobre.leer(objectMapper, mensajeJson, MatriculaEvent.class);

            if (sobre.getTraceId() != null) {
                MDC.put("traceId", sobre.getTraceId());
            }

            if (eventoProcesadoRepository.existsByEventId(sobre.getEventId())) {
                log.info("Evento ya procesado en calificaciones-ms: {}. Omitiendo.", sobre.getEventId());
                return;
            }

            eventoProcesadoRepository.save(EventoProcesado.builder()
                .eventId(sobre.getEventId())
                .eventType(sobre.getEventType())
                .procesadoEn(LocalDateTime.now())
                .build());

            MatriculaEvent event = sobre.getPayload();
            matriculaLocalService.sincronizarMatricula(event);
        } catch (Exception e) {
            log.error("Error al procesar evento de matrícula en calificaciones-ms: {}", e.getMessage(), e);
        } finally {
            MDC.remove("traceId");
        }
    }
}

