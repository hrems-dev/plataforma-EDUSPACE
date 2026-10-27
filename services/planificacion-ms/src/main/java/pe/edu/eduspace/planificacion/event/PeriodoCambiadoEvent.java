package pe.edu.eduspace.planificacion.event;

import java.time.Instant;
import java.util.UUID;
import org.slf4j.MDC;

public record PeriodoCambiadoEvent(UUID eventId, String eventType, Instant occurredAt, int version, String traceId,
                                   Long periodoId, Short numero, String nombre, String estado, Long anioId)
        implements DomainEvent {

    public static final String TYPE = "periodo.cambiado";

    public static PeriodoCambiadoEvent of(Long periodoId, Short numero, String nombre, String estado, Long anioId) {
        return new PeriodoCambiadoEvent(UUID.randomUUID(), TYPE, Instant.now(), 1, MDC.get("traceId"),
                periodoId, numero, nombre, estado, anioId);
    }

    @Override
    public String key() {
        return String.valueOf(periodoId);
    }
}
