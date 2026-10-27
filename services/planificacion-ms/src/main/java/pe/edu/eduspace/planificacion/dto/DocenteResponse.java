package pe.edu.eduspace.planificacion.dto;

import pe.edu.eduspace.planificacion.entity.EstadoDocente;

public record DocenteResponse(Long id, Long usuarioId, String tipoDocumento, String numeroDocumento, String nombres,
                              String apellidos, String email, String telefono, String especialidad,
                              EstadoDocente estado) {
}
