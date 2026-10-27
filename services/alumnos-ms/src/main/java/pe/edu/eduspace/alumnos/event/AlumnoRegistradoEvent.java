package pe.edu.eduspace.alumnos.event;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.slf4j.MDC;

/** Contrato {@code alumno.registrado}. Privacidad de menores: sin DNI, salud ni documentos. */
public record AlumnoRegistradoEvent(UUID eventId, String eventType, Instant occurredAt, int version, String traceId,
                                    Long alumnoId, String codigo, String nombreCompleto, LocalDate fechaNacimiento,
                                    List<Long> apoderadoIds, List<Long> usuarioIds, LocalDateTime fecha)
        implements DomainEvent {

    public static final String TYPE = "alumno.registrado";

    public static AlumnoRegistradoEvent of(Long alumnoId, String codigo, String nombreCompleto,
                                           LocalDate fechaNacimiento, List<Long> apoderadoIds, List<Long> usuarioIds,
                                           LocalDateTime fecha) {
        return new AlumnoRegistradoEvent(UUID.randomUUID(), TYPE, Instant.now(), 1, MDC.get("traceId"), alumnoId,
                codigo, nombreCompleto, fechaNacimiento, apoderadoIds, usuarioIds, fecha);
    }

    @Override
    public String key() {
        return String.valueOf(alumnoId);
    }
}
