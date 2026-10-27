package pe.edu.eduspace.planificacion.dto;

import jakarta.validation.constraints.NotNull;
import pe.edu.eduspace.planificacion.entity.EstadoPeriodo;

public record CambioEstadoRequest(@NotNull EstadoPeriodo estado) {
}
