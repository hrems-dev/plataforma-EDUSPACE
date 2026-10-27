package pe.edu.eduspace.planificacion.dto;

import pe.edu.eduspace.planificacion.entity.TipoAula;

public record AulaResponse(Long id, String codigo, String nombre, TipoAula tipo, Short capacidad, Boolean activo) {
}
