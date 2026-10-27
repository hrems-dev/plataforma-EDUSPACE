package pe.edu.upeu.eduspace.calificaciones.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

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
    private BigDecimal nota; // Escala 0-20
    private BigDecimal puntajeMaximo;
}

