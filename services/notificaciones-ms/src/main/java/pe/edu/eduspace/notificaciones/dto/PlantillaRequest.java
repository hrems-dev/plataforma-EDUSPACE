package pe.edu.eduspace.notificaciones.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PlantillaRequest(@Size(max = 200) String asunto, @NotBlank String cuerpo, Boolean activa) {
}
