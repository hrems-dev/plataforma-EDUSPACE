package pe.edu.eduspace.planificacion.event;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.slf4j.MDC;

public record AnioActivadoEvent(UUID eventId, String eventType, Instant occurredAt, int version, String traceId,
                                Long anioId, String nombre, LocalDate fechaInicio, LocalDate fechaFin)
        implements DomainEvent {

    public static final String TYPE = "anio.activado";

    public static AnioActivadoEvent of(Long anioId, String nombre, LocalDate fechaInicio, LocalDate fechaFin) {
        return new AnioActivadoEvent(UUID.randomUUID(), TYPE, Instant.now(), 1, MDC.get("traceId"),
                anioId, nombre, fechaInicio, fechaFin);
    }

    @Override
    public String key() {
        return String.valueOf(anioId);
    }
}
