package pe.edu.upeu.eduspace.pago.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PagoDetalleRequest {

    @NotNull(message = "El cuotaId es obligatorio")
    private Long cuotaId;

    @NotNull(message = "El montoAplicado es obligatorio")
    @DecimalMin(value = "0.01", message = "El monto aplicado debe ser mayor a 0")
    private BigDecimal montoAplicado;
}

