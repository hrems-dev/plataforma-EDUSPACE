package pe.edu.eduspace.alumnos.dto;

import pe.edu.eduspace.alumnos.entity.Parentesco;

public record VinculoResponse(Long id, ApoderadoResponse apoderado, Parentesco parentesco, Boolean esPrincipal,
                              Boolean esResponsablePago, Boolean viveConAlumno, Boolean puedeRecoger) {
}
