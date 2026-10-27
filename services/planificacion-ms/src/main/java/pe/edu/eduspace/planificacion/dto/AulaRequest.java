package pe.edu.eduspace.planificacion.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pe.edu.eduspace.planificacion.entity.TipoAula;

public record AulaRequest(
        @NotBlank @Size(max = 20) String codigo,
        @NotBlank @Size(max = 80) String nombre,
        TipoAula tipo,
        @NotNull @Min(1) Short capacidad,
        Boolean activo) {
}
