package pe.edu.eduspace.notificaciones.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BoletinGeneradoEvent(UUID eventId, Long alumnoId, Long boletinId, String periodoNombre) {
}
