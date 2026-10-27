package pe.edu.eduspace.alumnos.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.eduspace.alumnos.event.AlumnoEstadoCambiadoEvent;
import pe.edu.eduspace.alumnos.storage.AlmacenamientoArchivos;

/** El evento solo sale a Kafka si la transacción de negocio se confirma. */
class EventoDespuesDelCommitIntegrationTest extends PostgresIntegrationTestBase {

    @MockitoBean KafkaTemplate<String, String> kafkaTemplate;
    @MockitoBean AlmacenamientoArchivos almacenamiento;
    @Autowired ApplicationEventPublisher publisher;
    @Autowired TransactionTemplate tx;

    @BeforeEach
    void limpiar() {
        reset(kafkaTemplate);
        when(kafkaTemplate.send(any(String.class), any(String.class), any(String.class))).thenReturn(CompletableFuture.completedFuture(null));
    }

    private AlumnoEstadoCambiadoEvent evento() {
        return AlumnoEstadoCambiadoEvent.of(31L, "ACTIVO", "RETIRADO", "Traslado", LocalDateTime.now());
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
        tx.executeWithoutResult(status -> publisher.publishEvent(evento()));
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("alumno.estado-cambiado"), eq("31"), json.capture());
        assertThat(json.getValue())
                .contains("\"eventType\":\"alumno.estado-cambiado\"", "\"version\":1", "\"alumnoId\":31",
                        "\"estadoAnterior\":\"ACTIVO\"", "\"estadoNuevo\":\"RETIRADO\"", "\"eventId\":\"")
                .doesNotContain("@class", "__TypeId__");
    }
}
