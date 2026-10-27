package pe.edu.eduspace.planificacion.dto;

import java.time.LocalDate;

public record FeriadoResponse(Long id, Long anioId, LocalDate fecha, String descripcion) {
}
