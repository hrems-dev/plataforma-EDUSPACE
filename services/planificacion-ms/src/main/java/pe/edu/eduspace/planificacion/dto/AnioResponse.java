package pe.edu.eduspace.planificacion.dto;

import java.time.LocalDate;
import pe.edu.eduspace.planificacion.entity.EstadoPeriodo;

public record AnioResponse(Long id, String nombre, LocalDate fechaInicio, LocalDate fechaFin, EstadoPeriodo estado) {
}
