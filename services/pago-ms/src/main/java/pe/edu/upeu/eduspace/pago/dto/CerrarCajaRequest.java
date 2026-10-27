package pe.edu.upeu.eduspace.pago.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CerrarCajaRequest {

    @NotNull(message = "El monto declarado es obligatorio")
    @DecimalMin(value = "0.00", message = "El monto no puede ser negativo")
    private BigDecimal montoCierreDeclarado;
}

