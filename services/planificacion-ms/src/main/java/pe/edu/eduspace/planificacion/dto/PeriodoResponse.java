package pe.edu.eduspace.planificacion.dto;

import java.time.LocalDate;
import pe.edu.eduspace.planificacion.entity.EstadoPeriodo;

public record PeriodoResponse(Long id, Long anioId, Short numero, String nombre, LocalDate fechaInicio,
                              LocalDate fechaFin, EstadoPeriodo estado) {
}
