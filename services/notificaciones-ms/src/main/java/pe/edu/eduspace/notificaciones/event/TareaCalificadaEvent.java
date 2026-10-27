package pe.edu.eduspace.notificaciones.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TareaCalificadaEvent(UUID eventId, Long alumnoId, Long tareaId, Long entregaId, String titulo,
                                   BigDecimal nota) {
}
