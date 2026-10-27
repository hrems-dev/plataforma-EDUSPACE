package pe.edu.upeu.eduspace.asistencia.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class Dtos {

    private Dtos() {}

    public record SesionCreateRequest(
        @NotNull(message = "seccionId es obligatorio") Long seccionId,
        @NotNull(message = "fecha es obligatoria") LocalDate fecha,
        @NotBlank(message = "tipo es obligatorio") @Pattern(regexp = "DIA|CURSO", message = "tipo debe ser DIA o CURSO") String tipo,
        Long cursoId,
        Long bloqueId,
        Long docenteId) {}

    public record ItemRegistro(
        @NotNull(message = "matriculaId es obligatorio") Long matriculaId,
        @NotBlank(message = "estado es obligatorio") @Pattern(regexp = "PRESENTE|FALTA|TARDANZA", message = "estado debe ser PRESENTE, FALTA o TARDANZA") String estado,
        @Min(value = 1, message = "minutosTarde debe ser mayor a 0") Short minutosTarde,
        @Size(max = 300) String observacion) {}

    public record RegistroAsistenciaRequest(@NotEmpty(message = "Debe incluir al menos un alumno") @Valid List<ItemRegistro> items) {}

    public record CorreccionRequest(
        @NotBlank @Pattern(regexp = "PRESENTE|FALTA|TARDANZA|JUSTIFICADO", message = "estado inválido") String estado,
        @Min(1) Short minutosTarde,
        @NotBlank(message = "El motivo es obligatorio") @Size(max = 300) String motivo) {}

    public record DetalleResponse(Long detalleId, Long matriculaId, Long alumnoId, String alumnoNombre, String estado,
                                  Short minutosTarde, String observacion, boolean confirmado) {}

    public record SesionResponse(Long id, Long anioId, Long periodoId, Long seccionId, LocalDate fecha, String tipo,
                                 Long cursoId, Long bloqueId, Long docenteId, String estado, LocalDateTime cerradaEn,
                                 List<DetalleResponse> alumnos) {}

    public record JustificacionRequest(
        @NotNull Long alumnoId,
        @NotNull LocalDate fechaDesde,
        @NotNull LocalDate fechaHasta,
        @NotBlank @Pattern(regexp = "MEDICA|FAMILIAR|OTRO", message = "tipo debe ser MEDICA, FAMILIAR u OTRO") String tipo,
        @NotBlank @Size(max = 500) String motivo) {}

    public record ResolverJustificacionRequest(
        @NotBlank @Pattern(regexp = "APROBADA|RECHAZADA", message = "resolucion debe ser APROBADA o RECHAZADA") String resolucion,
        @Size(max = 300) String comentario) {}

    public record JustificacionResponse(Long id, Long matriculaId, Long alumnoId, LocalDate fechaDesde, LocalDate fechaHasta,
                                        String tipo, String motivo, String rutaArchivo, String estado, Long solicitadoPor,
                                        Long resueltoPor, LocalDateTime resueltoEn, String comentarioResolucion,
                                        LocalDateTime creadoEn, int faltasJustificadas) {}

    public record ArchivoUrlRequest(@NotBlank String nombreArchivo, @NotBlank String contentType) {}

    public record ArchivoUrlResponse(String uploadUrl, String ruta, int expiraMinutos) {}

    public record ConteoAsistencia(int sesiones, int presentes, int faltas, int tardanzas, int justificadas, double porcentajeAsistencia) {}

    public record ResumenAlumnoResponse(Long alumnoId, LocalDate desde, LocalDate hasta, Long periodoId,
                                        ConteoAsistencia dias, ConteoAsistencia cursos) {}

    public record FilaReporte(Long alumnoId, Long matriculaId, String alumnoNombre, ConteoAsistencia dias, boolean excesoFaltas) {}

    public record ReporteSeccionResponse(Long seccionId, LocalDate desde, LocalDate hasta, int totalAlumnos,
                                         List<FilaReporte> alumnos, List<FilaReporte> alertasExcesoFaltas) {}

    public record AsistenciaHijoResponse(Long alumnoId, String alumnoNombre, ResumenAlumnoResponse resumen, List<DetalleDia> ultimos) {}

    public record DetalleDia(LocalDate fecha, String tipo, Long cursoId, String estado, Short minutosTarde) {}
}
