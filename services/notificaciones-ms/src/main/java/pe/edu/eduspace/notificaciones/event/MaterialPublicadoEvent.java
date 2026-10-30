package pe.edu.eduspace.notificaciones.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MaterialPublicadoEvent(UUID eventId, Long materialId, String cursoNombre, String titulo, List<Long> alumnoIds) {
}
