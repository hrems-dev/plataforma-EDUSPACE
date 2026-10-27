package pe.edu.eduspace.planificacion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Edición menor de niveles y grados: solo nombre y estado. */
public record CatalogoUpdateRequest(@NotBlank @Size(max = 50) String nombre, Boolean activo) {
}
