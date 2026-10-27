package pe.edu.eduspace.planificacion.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.eduspace.planificacion.event.AnioActivadoEvent;

/** El evento solo sale a Kafka si la transacción de negocio se confirma. */
class EventoDespuesDelCommitIntegrationTest extends PostgresIntegrationTestBase {

    @MockitoBean KafkaTemplate<String, String> kafkaTemplate;
    @Autowired ApplicationEventPublisher publisher;
    @Autowired TransactionTemplate tx;

    @BeforeEach
    void limpiar() {
        reset(kafkaTemplate);
        when(kafkaTemplate.send(any(String.class), any(String.class), any(String.class))).thenReturn(CompletableFuture.completedFuture(null));
    }

    private AnioActivadoEvent evento() {
        return AnioActivadoEvent.of(99L, "2099", LocalDate.of(2099, 3, 1), LocalDate.of(2099, 12, 1));
    }

    @Test
    void siLaTransaccionSeRevierteNoSaleNingunEvento() {
        tx.executeWithoutResult(status -> {
            publisher.publishEvent(evento());
            status.setRollbackOnly();
        });
        verify(kafkaTemplate, never()).send(any(String.class), any(String.class), any(String.class));
    }

    @Test
    void siLaTransaccionSeConfirmaSaleUnSoloEventoConSuSobre() {
        tx.executeWithoutResult(status -> {
            publisher.publishEvent(evento());
            verify(kafkaTemplate, never()).send(any(String.class), any(String.class), any(String.class));
        });
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("anio.activado"), eq("99"), json.capture());
        assertThat(json.getValue())
                .contains("\"eventType\":\"anio.activado\"", "\"version\":1", "\"anioId\":99", "\"nombre\":\"2099\"")
                .contains("\"eventId\":\"", "\"occurredAt\":\"")
                .doesNotContain("@class", "__TypeId__");
    }
}
