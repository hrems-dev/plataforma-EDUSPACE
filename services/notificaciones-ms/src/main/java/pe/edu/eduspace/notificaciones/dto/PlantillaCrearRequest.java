package pe.edu.eduspace.notificaciones.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import pe.edu.eduspace.notificaciones.entity.Canal;

/** Alta de una plantilla propia. El código va en MAYÚSCULAS con guion bajo (p. ej. {@code COMUNICADO_FERIADO}). */
public record PlantillaCrearRequest(
        @NotBlank @Size(max = 60) @Pattern(regexp = "[A-Z][A-Z0-9_]{2,59}", message = "usa mayúsculas, números y guion bajo (mínimo 3 caracteres)") String codigo,
        @NotNull Canal canal,
        @Size(max = 200) String asunto,
        @NotBlank String cuerpo,
        Boolean activa) {
}
