package pe.edu.eduspace.planificacion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record FeriadoRequest(@NotNull Long anioId, @NotNull LocalDate fecha,
                             @NotBlank @Size(max = 150) String descripcion) {
}
