package pe.edu.eduspace.planificacion.dto;

import java.time.LocalTime;
import pe.edu.eduspace.planificacion.entity.Turno;

public record BloqueResponse(Long id, Turno turno, Short orden, LocalTime horaInicio, LocalTime horaFin,
                             Boolean esReceso) {
}
