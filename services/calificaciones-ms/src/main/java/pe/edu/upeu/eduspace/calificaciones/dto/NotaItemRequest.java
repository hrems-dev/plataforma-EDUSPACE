package pe.edu.upeu.eduspace.calificaciones.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotaItemRequest {

    @NotNull(message = "matriculaId es obligatorio")
    private Long matriculaId;

    @NotNull(message = "alumnoId es obligatorio")
    private Long alumnoId;

    @NotNull(message = "El valor de la nota es obligatorio")
    @DecimalMin(value = "0.00", message = "La nota mínima es 0")
    @DecimalMax(value = "20.00", message = "La nota máxima es 20")
    private BigDecimal valor;
}

