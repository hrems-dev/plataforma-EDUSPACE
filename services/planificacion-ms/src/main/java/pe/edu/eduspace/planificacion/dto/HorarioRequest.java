package pe.edu.eduspace.planificacion.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record HorarioRequest(@NotNull Long asignacionId, @NotNull Long bloqueId, Long aulaId,
                             @NotNull @Min(1) @Max(7) Short diaSemana) {
}
