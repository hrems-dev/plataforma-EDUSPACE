package pe.edu.eduspace.alumnos.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import pe.edu.eduspace.alumnos.entity.Parentesco;

/** Vincula un apoderado: uno existente ({@code apoderadoId}) o sus datos (se reutiliza si el documento ya existe). */
public record VinculoApoderadoRequest(
        Long apoderadoId,
        @Valid ApoderadoRequest apoderado,
        @NotNull Parentesco parentesco,
        Boolean esPrincipal,
        Boolean esResponsablePago,
        Boolean viveConAlumno,
        Boolean puedeRecoger) {
}
