package pe.edu.upeu.eduspace.pago.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class TarifaRequest {

    @NotNull(message = "El anioId es obligatorio")
    private Long anioId;

    @NotNull(message = "El nivelId es obligatorio")
    private Long nivelId;

    private Long gradoId;

    @NotNull(message = "El conceptoId es obligatorio")
    private Long conceptoId;

    @NotNull(message = "El monto es obligatorio")
    @DecimalMin(value = "0.00", message = "El monto no puede ser negativo")
    private BigDecimal monto;

    @NotNull(message = "El número de cuotas es obligatorio")
    @Min(1)
    @Max(12)
    private Short numCuotas = 1;

    @NotNull(message = "El mes de inicio es obligatorio")
    @Min(1)
    @Max(12)
    private Short mesInicio = 3;

    @NotNull(message = "El día de vencimiento es obligatorio")
    @Min(1)
    @Max(28)
    private Short diaVencimiento = 5;

    private Boolean activo = true;
}

