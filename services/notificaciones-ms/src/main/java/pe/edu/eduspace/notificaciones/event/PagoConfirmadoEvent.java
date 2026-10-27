package pe.edu.eduspace.notificaciones.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PagoConfirmadoEvent(UUID eventId, Long alumnoId, Long pagoId, BigDecimal monto, String concepto) {
}
