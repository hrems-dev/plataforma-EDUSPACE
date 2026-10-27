package pe.edu.eduspace.planificacion.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record MallaHorasRequest(@NotNull @Min(1) Short horasSemanales) {
}
