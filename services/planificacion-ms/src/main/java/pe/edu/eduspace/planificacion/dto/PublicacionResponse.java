package pe.edu.eduspace.planificacion.dto;

import java.time.LocalDateTime;

public record PublicacionResponse(Long id, Long seccionId, Integer version, Long publicadoPor,
                                  LocalDateTime publicadoEn) {
}
