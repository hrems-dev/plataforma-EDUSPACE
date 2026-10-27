package pe.edu.upeu.eduspace.calificaciones.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CriterioRequest {

    @NotNull(message = "anioId es obligatorio")
    private Long anioId;

    @NotNull(message = "periodoId es obligatorio")
    private Long periodoId;

    @NotNull(message = "seccionId es obligatorio")
    private Long seccionId;

    @NotNull(message = "cursoId es obligatorio")
    private Long cursoId;

    @NotBlank(message = "El nombre del criterio es obligatorio")
    @Size(max = 80, message = "El nombre no puede exceder 80 caracteres")
    private String nombre;

    @NotNull(message = "El peso del criterio es obligatorio")
    @DecimalMin(value = "0.01", message = "El peso debe ser mayor a 0")
    @DecimalMax(value = "100.00", message = "El peso no puede exceder 100")
    private BigDecimal peso;

    @Pattern(regexp = "MANUAL|TAREAS|EXAMENES", message = "origenTipo debe ser MANUAL, TAREAS o EXAMENES")
    @Builder.Default
    private String origenTipo = "MANUAL";
}

