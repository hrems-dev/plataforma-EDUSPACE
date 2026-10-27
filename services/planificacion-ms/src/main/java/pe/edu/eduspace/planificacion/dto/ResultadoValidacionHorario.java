package pe.edu.eduspace.planificacion.dto;

import java.util.List;

public record ResultadoValidacionHorario(boolean valido, List<String> choques,
                                         List<CursoHorasResponse> cursosIncompletos) {
}
