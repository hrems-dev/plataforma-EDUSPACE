package pe.edu.eduspace.alumnos.event;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import org.slf4j.MDC;

public record AlumnoEstadoCambiadoEvent(UUID eventId, String eventType, Instant occurredAt, int version,
                                        String traceId, Long alumnoId, String estadoAnterior, String estadoNuevo,
                                        String motivo, LocalDateTime fecha) implements DomainEvent {

    public static final String TYPE = "alumno.estado-cambiado";

    public static AlumnoEstadoCambiadoEvent of(Long alumnoId, String estadoAnterior, String estadoNuevo,
                                               String motivo, LocalDateTime fecha) {
        return new AlumnoEstadoCambiadoEvent(UUID.randomUUID(), TYPE, Instant.now(), 1, MDC.get("traceId"),
                alumnoId, estadoAnterior, estadoNuevo, motivo, fecha);
    }

    @Override
    public String key() {
        return String.valueOf(alumnoId);
    }
}
