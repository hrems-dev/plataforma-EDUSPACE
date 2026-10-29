package pe.edu.eduspace.notificaciones.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NotificacionEditarRequest(@NotBlank @Size(max = 200) String titulo, @NotBlank String mensaje) {
}
