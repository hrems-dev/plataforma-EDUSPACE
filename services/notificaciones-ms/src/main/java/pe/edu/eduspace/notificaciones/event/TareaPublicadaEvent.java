package pe.edu.eduspace.notificaciones.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TareaPublicadaEvent(UUID eventId, Long tareaId, String cursoNombre, String titulo, Object fechaEntrega,
                                  List<Long> alumnoIds) {
}
