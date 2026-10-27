package pe.edu.eduspace.planificacion.dto;

import pe.edu.eduspace.planificacion.entity.Turno;

public record SeccionInternaResponse(Long id, String nombre, Long gradoId, Long nivelId, Long anioId,
                                     Short capacidad, Turno turno, Long tutorDocenteId) {
}
