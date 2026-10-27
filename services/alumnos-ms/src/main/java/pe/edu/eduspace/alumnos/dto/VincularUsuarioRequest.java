package pe.edu.eduspace.alumnos.dto;

import jakarta.validation.constraints.NotNull;

public record VincularUsuarioRequest(@NotNull Long usuarioId) {
}
