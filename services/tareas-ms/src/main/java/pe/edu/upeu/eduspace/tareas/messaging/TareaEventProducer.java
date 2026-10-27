package pe.edu.upeu.eduspace.tareas.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import pe.edu.upeu.eduspace.tareas.event.EventoSobre;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.UUID;

/** Publica eventos con el sobre estándar. Dentro de una transacción espera al commit; si hace rollback no envía nada. */
@Slf4j
@Component
@RequiredArgsConstructor
public class TareaEventProducer {

    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper objectMapper;

    public <T> void publicar(String topic, String clave, T payload) {
        String json = EventoSobre.escribir(objectMapper, EventoSobre.<T>builder()
            .eventId(UUID.randomUUID())
            .eventType(topic)
            .occurredAt(LocalDateTime.now())
            .version(1)
            .traceId(MDC.get("traceId") != null ? MDC.get("traceId") : UUID.randomUUID().toString())
            .payload(payload)
            .build());
        Runnable enviar = () -> kafka.send(topic, clave, json).whenComplete((r, ex) -> {
            if (ex != null) log.error("No se pudo publicar {} (clave {}): {}", topic, clave, ex.getMessage());
            else log.debug("Evento {} publicado (clave {})", topic, clave);
        });
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    enviar.run();
                }
            });
        } else {
            enviar.run();
        }
    }
}
