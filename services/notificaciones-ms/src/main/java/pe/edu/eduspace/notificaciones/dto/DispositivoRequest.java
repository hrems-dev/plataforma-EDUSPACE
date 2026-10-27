package pe.edu.eduspace.notificaciones.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pe.edu.eduspace.notificaciones.entity.Plataforma;

public record DispositivoRequest(@NotBlank @Size(max = 400) String token, @NotNull Plataforma plataforma) {
}
