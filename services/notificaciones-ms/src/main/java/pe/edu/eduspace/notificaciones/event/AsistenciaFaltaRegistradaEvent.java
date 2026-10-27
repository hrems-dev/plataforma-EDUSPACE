package pe.edu.eduspace.notificaciones.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AsistenciaFaltaRegistradaEvent(UUID eventId, Long alumnoId, Long asistenciaId, Long asistenciaDetalleId,
                                             Object fecha) {
}
