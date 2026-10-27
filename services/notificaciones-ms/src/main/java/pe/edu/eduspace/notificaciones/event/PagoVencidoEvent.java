package pe.edu.eduspace.notificaciones.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.util.UUID;

/** Sirve para {@code pago.vencido} y {@code pago.por-vencer}. La fecha se lee como objeto: puede llegar como texto o como arreglo. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PagoVencidoEvent(UUID eventId, Long alumnoId, Long cuotaId, String concepto, BigDecimal monto,
                               BigDecimal saldo, Object fechaVencimiento) {
}
