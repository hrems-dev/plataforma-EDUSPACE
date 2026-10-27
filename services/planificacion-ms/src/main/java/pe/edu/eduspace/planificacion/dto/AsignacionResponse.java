package pe.edu.eduspace.planificacion.dto;

public record AsignacionResponse(Long id, Long seccionId, String seccionNombre, Long cursoId, String cursoNombre,
                                 Long docenteId, String docenteNombre) {
}
