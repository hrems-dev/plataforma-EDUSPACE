package pe.edu.eduspace.planificacion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record AnioRequest(
        @NotBlank @Size(max = 20) String nombre,
        @NotNull LocalDate fechaInicio,
        @NotNull LocalDate fechaFin) {
}
