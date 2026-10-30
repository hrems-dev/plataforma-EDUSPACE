package pe.edu.eduspace.alumnos.dto;

import pe.edu.eduspace.alumnos.entity.EstadoAlumno;

/** Fila de listado. {@code usuarioId}, {@code nombres} y {@code apellidoPaterno} sirven para crear y reconocer la cuenta de acceso. */
public record AlumnoResumenResponse(Long id, String codigo, String nombreCompleto, String tipoDocumento,
                                    String numeroDocumento, EstadoAlumno estado, Long usuarioId, String nombres,
                                    String apellidoPaterno) {
}
