package pe.edu.eduspace.planificacion.event;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.slf4j.MDC;

/**
 * Contrato {@code horario.publicado}. La versión del horario viaja en {@code versionHorario} porque
 * {@code version} del sobre estándar es la versión del contrato.
 */
public record HorarioPublicadoEvent(UUID eventId, String eventType, Instant occurredAt, int version, String traceId,
                                    Long seccionId, String seccionNombre, Long anioId, Integer versionHorario,
                                    List<Long> docenteUsuarioIds, LocalDateTime fecha) implements DomainEvent {

    public static final String TYPE = "horario.publicado";

    public static HorarioPublicadoEvent of(Long seccionId, String seccionNombre, Long anioId, Integer versionHorario,
                                           List<Long> docenteUsuarioIds, LocalDateTime fecha) {
        return new HorarioPublicadoEvent(UUID.randomUUID(), TYPE, Instant.now(), 1, MDC.get("traceId"),
                seccionId, seccionNombre, anioId, versionHorario, docenteUsuarioIds, fecha);
    }

    @Override
    public String key() {
        return String.valueOf(seccionId);
    }
}
