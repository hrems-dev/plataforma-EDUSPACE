package pe.edu.eduspace.planificacion.dto;

import jakarta.validation.constraints.NotNull;

public record AsignacionRequest(@NotNull Long seccionId, @NotNull Long cursoId, @NotNull Long docenteId) {
}
