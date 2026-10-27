package pe.edu.eduspace.notificaciones.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pe.edu.eduspace.notificaciones.entity.Canal;

public record PreferenciaRequest(@NotBlank @Size(max = 60) String tipo, @NotNull Canal canal, @NotNull Boolean habilitada) {
}
