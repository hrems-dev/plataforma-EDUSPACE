package pe.edu.eduspace.planificacion.dto;

import jakarta.validation.constraints.NotNull;

public record VincularUsuarioRequest(@NotNull Long usuarioId) {
}
