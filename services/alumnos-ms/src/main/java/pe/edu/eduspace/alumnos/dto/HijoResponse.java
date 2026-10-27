package pe.edu.eduspace.alumnos.dto;

import pe.edu.eduspace.alumnos.entity.EstadoAlumno;
import pe.edu.eduspace.alumnos.entity.Parentesco;

/** Un alumno visto desde su apoderado. */
public record HijoResponse(Long alumnoId, String codigo, String nombreCompleto, EstadoAlumno estado,
                           Parentesco parentesco) {
}
