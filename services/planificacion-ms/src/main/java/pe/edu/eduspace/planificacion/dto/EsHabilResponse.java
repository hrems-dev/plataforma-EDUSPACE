package pe.edu.eduspace.planificacion.dto;

import java.time.LocalDate;

public record EsHabilResponse(LocalDate fecha, boolean habil, String motivo) {
}
