package pe.edu.eduspace.planificacion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CursoRequest(
        @NotBlank @Size(max = 20) String codigo,
        @NotBlank @Size(max = 100) String nombre,
        @Size(max = 60) String area,
        Boolean activo) {
}
