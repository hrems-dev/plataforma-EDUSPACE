package pe.edu.upeu.eduspace.calificaciones.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import pe.edu.upeu.eduspace.calificaciones.config.KafkaConfig;
import pe.edu.upeu.eduspace.calificaciones.event.BoletinGeneradoEvent;
import pe.edu.upeu.eduspace.calificaciones.event.EventoSobre;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class BoletinEventProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void publicarBoletinGenerado(BoletinGeneradoEvent payload) {
        EventoSobre<BoletinGeneradoEvent> sobre = EventoSobre.<BoletinGeneradoEvent>builder()
            .eventId(UUID.randomUUID())
            .eventType(KafkaConfig.TOPIC_BOLETIN_GENERADO)
            .occurredAt(LocalDateTime.now())
            .version(1)
            .traceId(UUID.randomUUID().toString())
            .payload(payload)
            .build();

        String key = String.valueOf(payload.getBoletinId());
        kafkaTemplate.send(KafkaConfig.TOPIC_BOLETIN_GENERADO, key, EventoSobre.escribir(objectMapper, sobre));
        log.info("Evento boletin.generado publicado: boletinId={}, alumnoId={}", payload.getBoletinId(), payload.getAlumnoId());
    }
}

