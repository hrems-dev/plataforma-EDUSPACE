package pe.edu.eduspace.planificacion.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record PeriodoRequest(
        @NotNull Long anioId,
        @NotNull @Min(1) @Max(12) Short numero,
        @NotBlank @Size(max = 40) String nombre,
        @NotNull LocalDate fechaInicio,
        @NotNull LocalDate fechaFin) {
}
