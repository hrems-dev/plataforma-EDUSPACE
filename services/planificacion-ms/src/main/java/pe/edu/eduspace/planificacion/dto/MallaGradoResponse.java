package pe.edu.eduspace.planificacion.dto;

import java.util.List;

public record MallaGradoResponse(Long gradoId, String gradoNombre, int totalHoras, List<MallaItemResponse> cursos) {
}
