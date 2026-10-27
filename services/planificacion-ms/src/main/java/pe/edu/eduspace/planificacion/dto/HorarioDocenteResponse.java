package pe.edu.eduspace.planificacion.dto;

import java.util.List;

public record HorarioDocenteResponse(Long docenteId, String docenteNombre, List<HorarioResponse> clases) {
}
