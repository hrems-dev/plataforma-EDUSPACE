package pe.edu.eduspace.planificacion.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record MallaRequest(@NotNull Long gradoId, @NotNull Long cursoId, @NotNull @Min(1) Short horasSemanales) {
}
