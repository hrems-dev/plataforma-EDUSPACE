package pe.edu.upeu.eduspace.matricula.dto;

import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** DTOs de la API de matrícula agrupados por tamaño; todos son records inmutables. */
public final class Dtos {

    private Dtos() {}

    public record MatriculaCreateRequest(
        @NotNull(message = "alumnoId es obligatorio") Long alumnoId,
        @NotNull(message = "seccionId es obligatorio") Long seccionId,
        @Pattern(regexp = "NUEVA|RATIFICACION|REINGRESO", message = "tipo debe ser NUEVA, RATIFICACION o REINGRESO") String tipo,
        @Size(max = 500) String observaciones) {}

    public record TrasladoRequest(
        @NotNull(message = "nuevaSeccionId es obligatorio") Long nuevaSeccionId,
        @NotBlank(message = "El motivo es obligatorio") @Size(max = 300) String motivo) {}

    public record RetiroRequest(@NotBlank(message = "El motivo es obligatorio") @Size(max = 300) String motivo) {}

    public record AnulacionRequest(@Size(max = 300) String motivo) {}

    public record RequisitoRequest(@NotNull(message = "cumplido es obligatorio") Boolean cumplido,
                                   @Size(max = 300) String observacion) {}

    /**
     * Ratificación al año siguiente. Individual: {@code matriculaAnteriorId}. Masiva: {@code seccionOrigenId}.
     * Destino: {@code seccionDestinoId}, o {@code gradoDestinoId} para elegir la primera sección con cupo.
     */
    public record RatificacionRequest(
        @NotNull(message = "anioDestinoId es obligatorio") Long anioDestinoId,
        Long matriculaAnteriorId,
        Long seccionOrigenId,
        Long seccionDestinoId,
        Long gradoDestinoId) {}

    public record CampaniaRequest(
        @NotNull Long anioId,
        @NotBlank @Size(max = 80) String nombre,
        @NotNull LocalDate fechaInicio,
        @NotNull LocalDate fechaFin) {}

    public record RequisitoResponse(Long id, String tipo, Boolean cumplido, String observacion, Long verificadoPor,
                                    LocalDateTime verificadoEn) {}

    public record HistorialResponse(Long id, String tipoCambio, String estadoAnterior, String estadoNuevo,
                                    Long seccionAnteriorId, Long seccionNuevaId, String motivo, Long usuarioId,
                                    LocalDateTime fecha) {}

    public record MatriculaResponse(Long id, String codigo, Long alumnoId, String alumnoNombre, Long anioId, Long nivelId,
                                    Long gradoId, Long seccionId, Long campaniaId, String tipo, String estado,
                                    Long matriculaAnteriorId, LocalDateTime venceEn, String observaciones,
                                    LocalDateTime creadoEn, LocalDateTime confirmadaEn, LocalDateTime cerradaEn,
                                    String motivoCierre) {}

    public record MatriculaDetalleResponse(MatriculaResponse matricula, List<RequisitoResponse> requisitos,
                                           List<HistorialResponse> historial) {}

    public record CupoResponse(Long seccionId, String seccionNombre, Long anioId, Long gradoId, Short capacidad,
                               Short ocupados, int vacantes) {}

    public record CampaniaResponse(Long id, Long anioId, String nombre, LocalDate fechaInicio, LocalDate fechaFin,
                                   String estado) {}

    public record AlumnoSeccionResponse(Long matriculaId, String codigo, Long alumnoId, String alumnoNombre, String estado) {}

    public record RatificacionResultado(int solicitadas, int creadas, List<MatriculaResponse> matriculas,
                                        List<ErrorRatificacion> errores) {}

    public record ErrorRatificacion(Long matriculaAnteriorId, Long alumnoId, String motivo) {}

    public record InternalMatricula(Long matriculaId, Long alumnoId, String alumnoNombre, Long anioId, Long nivelId,
                                    Long gradoId, Long seccionId, String estado, String tipo) {}
}
