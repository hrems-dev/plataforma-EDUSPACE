package pe.edu.eduspace.alumnos.dto;

import pe.edu.eduspace.alumnos.entity.EstadoAlumno;

public record AlumnoResumenResponse(Long id, String codigo, String nombreCompleto, String tipoDocumento,
                                    String numeroDocumento, EstadoAlumno estado) {
}
