package pe.edu.eduspace.notificaciones.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ExamenCalificadoEvent(UUID eventId, Long alumnoId, Long examenId, Long intentoId, String cursoNombre,
                                    String titulo, BigDecimal nota) {
}
