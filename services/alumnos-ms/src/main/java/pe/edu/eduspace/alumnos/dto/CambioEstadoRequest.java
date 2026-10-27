package pe.edu.eduspace.alumnos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pe.edu.eduspace.alumnos.entity.EstadoAlumno;

public record CambioEstadoRequest(@NotNull EstadoAlumno estado,
                                  @NotBlank(message = "el motivo es obligatorio") @Size(max = 300) String motivo) {
}
