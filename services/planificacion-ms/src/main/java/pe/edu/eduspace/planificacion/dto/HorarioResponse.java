package pe.edu.eduspace.planificacion.dto;

import java.time.LocalTime;

public record HorarioResponse(Long id, Long asignacionId, Long seccionId, String seccionNombre, Long cursoId,
                              String cursoNombre, Long docenteId, String docenteNombre, Long bloqueId,
                              Short bloqueOrden, LocalTime horaInicio, LocalTime horaFin, Long aulaId,
                              String aulaNombre, Short diaSemana) {
}
