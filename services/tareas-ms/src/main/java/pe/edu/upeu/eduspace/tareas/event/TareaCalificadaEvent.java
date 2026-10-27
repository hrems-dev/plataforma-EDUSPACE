package pe.edu.upeu.eduspace.tareas.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Contrato con calificaciones-ms y notificaciones-ms. {@code nota} ya está en escala 0–20. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TareaCalificadaEvent {
    private Long tareaId;
    private Long entregaId;
    private Long alumnoId;
    private Long matriculaId;
    private Long seccionId;
    private Long cursoId;
    private Long periodoId;
    private String titulo;
    private BigDecimal nota;
    private BigDecimal puntajeMaximo;
    private BigDecimal puntajeObtenido;
}
