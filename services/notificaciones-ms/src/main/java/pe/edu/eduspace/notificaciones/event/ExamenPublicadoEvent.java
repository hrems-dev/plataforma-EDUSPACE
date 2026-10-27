package pe.edu.eduspace.notificaciones.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ExamenPublicadoEvent(UUID eventId, Long examenId, String cursoNombre, String titulo, Object fechaInicio,
                                   List<Long> alumnoIds) {
}
