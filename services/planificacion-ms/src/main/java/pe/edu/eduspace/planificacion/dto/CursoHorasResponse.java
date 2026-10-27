package pe.edu.eduspace.planificacion.dto;

public record CursoHorasResponse(Long cursoId, String cursoNombre, int horasRequeridas, int horasProgramadas,
                                 String estado) {
}
