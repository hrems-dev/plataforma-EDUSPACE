package pe.edu.upeu.eduspace.tareas.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class Dtos {

    private Dtos() {}

    public record TareaCreateRequest(
        @NotNull Long periodoId,
        @NotNull Long seccionId,
        @NotNull Long cursoId,
        @NotBlank @Size(max = 200) String titulo,
        String descripcion,
        @NotNull LocalDateTime fechaEntrega,
        Boolean permiteTardia,
        @DecimalMin(value = "0.01", message = "puntajeMaximo debe ser mayor a 0") @DecimalMax(value = "999.99") BigDecimal puntajeMaximo) {}

    public record TareaUpdateRequest(
        @Size(max = 200) String titulo,
        String descripcion,
        LocalDateTime fechaEntrega,
        Boolean permiteTardia,
        @DecimalMin("0.01") BigDecimal puntajeMaximo) {}

    public record AdjuntoResponse(Long id, String nombreArchivo, String contentType, Long tamanoBytes) {}

    public record TareaResponse(Long id, Long anioId, Long periodoId, Long seccionId, Long cursoId, Long docenteId,
                                String titulo, String descripcion, LocalDateTime publicadaEn, LocalDateTime fechaEntrega,
                                Boolean permiteTardia, BigDecimal puntajeMaximo, String estado,
                                List<AdjuntoResponse> adjuntos, Integer entregadas, Integer calificadas, Integer pendientes) {}

    public record ArchivoUrlRequest(
        @NotBlank String nombreArchivo,
        @NotBlank String contentType,
        @NotNull @Positive Long tamanoBytes) {}

    public record ArchivoUrlResponse(String uploadUrl, String ruta, int expiraMinutos) {}

    public record ArchivoRef(
        @NotBlank @Size(max = 200) String nombreArchivo,
        @NotBlank @Size(max = 400) String ruta,
        @NotBlank String contentType,
        @NotNull @Positive Long tamanoBytes) {}

    public record EntregaRequest(@Size(max = 1000) String comentario, @Valid List<ArchivoRef> archivos) {}

    public record ArchivoResponse(Long id, String nombreArchivo, String contentType, Long tamanoBytes) {}

    public record EntregaResponse(Long id, Long tareaId, Long matriculaId, Long alumnoId, String alumnoNombre, String comentario,
                                  LocalDateTime entregadaEn, Boolean esTardia, Short version, String estado, BigDecimal nota,
                                  String retroalimentacion, LocalDateTime calificadaEn, List<ArchivoResponse> archivos) {}

    public record CalificarRequest(
        @NotNull(message = "La nota es obligatoria") @DecimalMin(value = "0", message = "La nota no puede ser negativa") BigDecimal nota,
        @Size(max = 4000) String retroalimentacion) {}

    public record DevolverRequest(@NotBlank(message = "Indique qué debe corregir el alumno") @Size(max = 4000) String retroalimentacion) {}

    public record NotaItem(@NotNull Long entregaId, @NotNull @DecimalMin("0") BigDecimal nota, @Size(max = 4000) String retroalimentacion) {}

    public record CalificacionMasivaRequest(@NotEmpty @Valid List<NotaItem> notas) {}

    public record CalificacionMasivaResultado(int calificadas, List<String> errores) {}

    /** Estado de cada alumno de la sección para la tarea (incluye los que no entregaron). */
    public record EntregaAlumno(Long matriculaId, Long alumnoId, String alumnoNombre, String estado, EntregaResponse entrega) {}

    public record TareaPendienteResponse(Long tareaId, String titulo, Long cursoId, LocalDateTime fechaEntrega, Boolean vencida,
                                         Boolean permiteTardia, Long alumnoId, String alumnoNombre, String estadoEntrega) {}

    public record DescargaResponse(String urlDescarga, String nombreArchivo) {}

    // ---------------------------------------------------------------- materiales del curso

    public record MaterialArchivoUrlRequest(
        @NotNull Long seccionId, @NotNull Long cursoId,
        @NotBlank String nombreArchivo, @NotBlank String contentType, @NotNull @Positive Long tamanoBytes) {}

    /** tipo ARCHIVO: lleva {@code archivo} (ya subido con la URL firmada); tipo ENLACE: lleva {@code enlace}. */
    public record MaterialRequest(
        @NotNull Long seccionId, @NotNull Long cursoId,
        @NotBlank @Size(min = 3, max = 150) String titulo, @Size(max = 1000) String descripcion,
        @NotBlank String tipo, @Size(max = 500) String enlace, @Valid ArchivoRef archivo) {}

    public record MaterialResponse(Long id, Long seccionId, Long cursoId, Long docenteId, String titulo, String descripcion, String tipo,
                                   String enlace, String nombreArchivo, String contentType, Long tamanoBytes, LocalDateTime creadoEn) {}
}
