package pe.edu.eduspace.planificacion.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;
import pe.edu.eduspace.planificacion.entity.Turno;

public record BloqueRequest(@NotNull Turno turno, @NotNull @Min(1) Short orden, @NotNull LocalTime horaInicio,
                            @NotNull LocalTime horaFin, Boolean esReceso) {
}
