package pe.edu.eduspace.alumnos.dto;

import pe.edu.eduspace.alumnos.entity.Parentesco;

public record ApoderadoContactoResponse(Long apoderadoId, Long usuarioId, String nombreCompleto, String email,
                                        String telefono, Boolean esPrincipal, Boolean esResponsablePago,
                                        Parentesco parentesco) {
}
