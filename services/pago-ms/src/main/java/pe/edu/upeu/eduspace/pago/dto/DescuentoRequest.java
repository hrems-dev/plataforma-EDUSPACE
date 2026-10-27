package pe.edu.upeu.eduspace.pago.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class DescuentoRequest {

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 100)
    private String nombre;

    @NotBlank(message = "El tipo es obligatorio")
    @Pattern(regexp = "PORCENTAJE|MONTO_FIJO", message = "Tipo debe ser PORCENTAJE o MONTO_FIJO")
    private String tipo;

    @NotNull(message = "El valor es obligatorio")
    @DecimalMin(value = "0.01", message = "El valor debe ser mayor a 0")
    private BigDecimal valor;

    @NotBlank(message = "aplicaA es obligatorio")
    @Pattern(regexp = "MATRICULA|PENSION|TODO", message = "aplicaA debe ser MATRICULA, PENSION o TODO")
    private String aplicaA = "PENSION";

    private Boolean activo = true;
}

