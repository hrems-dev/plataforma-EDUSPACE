package pe.edu.upeu.eduspace.calificaciones.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotaPendienteResponse {

    private String id;
    private String origen; // TAREA, EXAMEN
    private Long origenId;
    private String titulo;
    private Long alumnoId;
    private Long matriculaId;
    private Long seccionId;
    private Long cursoId;
    private Long periodoId;
    private BigDecimal valor;
    private LocalDateTime fechaEvento;
    /** PENDIENTE (sin criterio) o PERIODO_CERRADO (requiere reapertura). */
    private String motivo;
}

