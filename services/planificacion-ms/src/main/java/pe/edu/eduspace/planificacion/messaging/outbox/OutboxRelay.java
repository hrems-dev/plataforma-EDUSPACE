package pe.edu.eduspace.planificacion.messaging.outbox;

import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import pe.edu.eduspace.planificacion.event.DomainEvent;

/**
 * Publica a Kafka los eventos pendientes del outbox. Varias instancias pueden correr a la vez:
 * {@code FOR UPDATE SKIP LOCKED} reparte las filas sin duplicar. Ante un fallo corta el lote para
 * conservar el orden por clave y reintenta en el siguiente ciclo.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private record Fila(long id, String topico, String clave, String payload) {}

    private final JdbcClient jdbc;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final int lote;
    private final long timeoutEnvioMs;
    private final TransactionTemplate tx;

    public OutboxRelay(JdbcClient jdbc, KafkaTemplate<String, String> kafkaTemplate, PlatformTransactionManager txManager,
                       @Value("${app.outbox.lote:100}") int lote,
                       @Value("${app.outbox.timeout-envio-ms:5000}") long timeoutEnvioMs) {
        this.jdbc = jdbc;
        this.kafkaTemplate = kafkaTemplate;
        this.lote = lote;
        this.timeoutEnvioMs = timeoutEnvioMs;
        this.tx = new TransactionTemplate(txManager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Disparo inmediato tras confirmar la transacción de negocio (el scheduler cubre lo que falle). */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void alConfirmar(DomainEvent ignorado) {
        publicarPendientes();
    }

    @Scheduled(fixedDelayString = "${app.outbox.intervalo:PT2S}", initialDelayString = "${app.outbox.espera-inicial:PT5S}")
    public void programado() {
        publicarPendientes();
    }

    @Scheduled(cron = "${app.outbox.limpieza-cron:0 15 3 * * *}")
    @Transactional
    public void limpiarEnviados() {
        int n = jdbc.sql("DELETE FROM outbox_eventos WHERE enviado_en IS NOT NULL AND enviado_en < now() - interval '7 days'").update();
        if (n > 0) log.info("component=OutboxRelay status=LIMPIEZA eliminados={}", n);
    }

    public void publicarPendientes() {
        try {
            tx.executeWithoutResult(status -> lotePendiente());
        } catch (RuntimeException ex) {
            log.warn("component=OutboxRelay status=ERROR cause={}", ex.getMessage());
        }
    }

    private void lotePendiente() {
        List<Fila> filas = jdbc.sql("""
                SELECT id, topico, clave, payload FROM outbox_eventos
                WHERE enviado_en IS NULL ORDER BY id LIMIT :lote FOR UPDATE SKIP LOCKED""")
                .param("lote", lote)
                .query((rs, n) -> new Fila(rs.getLong("id"), rs.getString("topico"), rs.getString("clave"), rs.getString("payload")))
                .list();
        for (Fila f : filas) {
            try {
                kafkaTemplate.send(f.topico(), f.clave(), f.payload()).get(timeoutEnvioMs, TimeUnit.MILLISECONDS);
                jdbc.sql("UPDATE outbox_eventos SET enviado_en = now(), ultimo_error = NULL WHERE id = :id")
                        .param("id", f.id()).update();
                log.info("component=OutboxRelay topic={} key={} outboxId={} status=ENVIADO", f.topico(), f.clave(), f.id());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception ex) {
                String causa = String.valueOf(ex.getMessage());
                jdbc.sql("UPDATE outbox_eventos SET intentos = intentos + 1, ultimo_error = :e WHERE id = :id")
                        .param("e", causa.substring(0, Math.min(causa.length(), 500))).param("id", f.id()).update();
                log.error("component=OutboxRelay topic={} key={} outboxId={} status=ERROR cause={}", f.topico(), f.clave(), f.id(), causa);
                return;
            }
        }
    }
}
