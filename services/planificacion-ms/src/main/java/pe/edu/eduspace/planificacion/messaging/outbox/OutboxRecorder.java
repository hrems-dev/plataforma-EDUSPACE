package pe.edu.eduspace.planificacion.messaging.outbox;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import pe.edu.eduspace.planificacion.config.KafkaTopicsProperties;
import pe.edu.eduspace.planificacion.event.DomainEvent;
import tools.jackson.databind.ObjectMapper;

/**
 * Guarda cada evento de dominio en {@code outbox_eventos} dentro de la MISMA transacción del negocio
 * (BEFORE_COMMIT): o se confirman los dos o ninguno. Tras el commit despierta al relay para baja latencia.
 */
@Component
public class OutboxRecorder {

    private final JdbcClient jdbc;
    private final KafkaTopicsProperties topics;
    private final ObjectMapper objectMapper;

    public OutboxRecorder(JdbcClient jdbc, KafkaTopicsProperties topics, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.topics = topics;
        this.objectMapper = objectMapper;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void registrar(DomainEvent event) {
        jdbc.sql("INSERT INTO outbox_eventos (topico, clave, payload) VALUES (:topico, :clave, :payload)")
                .param("topico", topics.topicFor(event.eventType()))
                .param("clave", event.key())
                .param("payload", objectMapper.writeValueAsString(event))
                .update();
    }
}
