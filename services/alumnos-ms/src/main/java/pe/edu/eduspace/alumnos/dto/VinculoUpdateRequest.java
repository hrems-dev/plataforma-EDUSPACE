package pe.edu.eduspace.alumnos.dto;

import pe.edu.eduspace.alumnos.entity.Parentesco;

/** Campos nulos = sin cambio. */
public record VinculoUpdateRequest(Parentesco parentesco, Boolean esPrincipal, Boolean esResponsablePago,
                                   Boolean viveConAlumno, Boolean puedeRecoger) {
}
