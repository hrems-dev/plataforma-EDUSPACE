package pe.edu.eduspace.alumnos.dto;

import java.time.LocalDateTime;
import pe.edu.eduspace.alumnos.entity.EstadoAlumno;

public record HistorialResponse(Long id, EstadoAlumno estadoAnterior, EstadoAlumno estadoNuevo, String motivo,
                                Long usuarioId, LocalDateTime cambiadoEn) {
}
