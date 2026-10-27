package pe.edu.eduspace.planificacion.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pe.edu.eduspace.planificacion.entity.Turno;

public record SeccionRequest(
        @NotNull Long anioId,
        @NotNull Long gradoId,
        Long aulaId,
        @NotBlank @Size(max = 10) String nombre,
        Turno turno,
        @Min(1) Short capacidad,
        Long tutorDocenteId,
        Boolean activo) {
}
