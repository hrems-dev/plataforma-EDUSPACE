package pe.edu.eduspace.alumnos.dto;

import pe.edu.eduspace.alumnos.entity.EstadoAlumno;

public record AlumnoBasicoResponse(Long id, String codigo, String nombreCompleto, EstadoAlumno estado) {
}
