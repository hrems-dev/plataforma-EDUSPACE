package pe.edu.eduspace.planificacion.dto;

import pe.edu.eduspace.planificacion.entity.Turno;

public record SeccionResponse(Long id, Long anioId, String anioNombre, Long gradoId, String gradoNombre,
                              Long nivelId, Long aulaId, String aulaNombre, String nombre, Turno turno,
                              Short capacidad, Long tutorDocenteId, String tutorNombre, Boolean activo) {
}
