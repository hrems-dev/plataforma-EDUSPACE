package pe.edu.eduspace.planificacion.dto;

import pe.edu.eduspace.planificacion.entity.EstadoDocente;

public record InternalDocenteResponse(Long docenteId, String nombre, EstadoDocente estado) {
}
