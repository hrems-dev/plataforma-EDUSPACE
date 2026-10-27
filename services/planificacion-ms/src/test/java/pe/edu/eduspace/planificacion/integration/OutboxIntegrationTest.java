package pe.edu.eduspace.planificacion.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.eduspace.planificacion.event.AnioActivadoEvent;
import pe.edu.eduspace.planificacion.messaging.outbox.OutboxRelay;

/** Outbox: el evento queda guardado con el negocio y sobrevive a una caída de Kafka. */
class OutboxIntegrationTest extends PostgresIntegrationTestBase {

    @MockitoBean KafkaTemplate<String, String> kafkaTemplate;
    @Autowired ApplicationEventPublisher publisher;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcClient jdbc;
    @Autowired OutboxRelay relay;

    @BeforeEach
    void limpiar() {
        reset(kafkaTemplate);
        jdbc.sql("DELETE FROM outbox_eventos").update();
    }

    private long pendientes() {
        return jdbc.sql("SELECT count(*) FROM outbox_eventos WHERE enviado_en IS NULL").query(Long.class).single();
    }

    @Test
    void siKafkaFallaElEventoQuedaPendienteYSeReenviaDespues() {
        when(kafkaTemplate.send(any(String.class), any(String.class), any(String.class)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("kafka caído")));
        tx.executeWithoutResult(status -> publisher.publishEvent(AnioActivadoEvent.of(99L, "2099", java.time.LocalDate.of(2099, 3, 1), java.time.LocalDate.of(2099, 12, 1))));
        assertThat(pendientes()).isEqualTo(1);
        assertThat(jdbc.sql("SELECT intentos FROM outbox_eventos").query(Integer.class).single()).isGreaterThanOrEqualTo(1);

        when(kafkaTemplate.send(any(String.class), any(String.class), any(String.class)))
                .thenReturn(CompletableFuture.completedFuture(null));
        relay.publicarPendientes();
        assertThat(pendientes()).isZero();
    }

    @Test
    void siLaTransaccionSeRevierteNoQuedaNadaEnElOutbox() {
        tx.executeWithoutResult(status -> {
            publisher.publishEvent(AnioActivadoEvent.of(99L, "2099", java.time.LocalDate.of(2099, 3, 1), java.time.LocalDate.of(2099, 12, 1)));
            status.setRollbackOnly();
        });
        assertThat(jdbc.sql("SELECT count(*) FROM outbox_eventos").query(Long.class).single()).isZero();
    }
}
