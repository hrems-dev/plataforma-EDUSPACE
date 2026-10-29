package pe.edu.eduspace.planificacion.dto;

import java.util.List;

public record MallaLoteResponse(int creados, int actualizados, int omitidos, List<String> detalle) {
}
