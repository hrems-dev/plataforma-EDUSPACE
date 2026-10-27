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
public class ExamenCalificadoEvent {

    private Long examenId;
    private Long intentoId;
    private Long alumnoId;
    private Long matriculaId;
    private Long seccionId;
    private Long cursoId;
    private Long periodoId;
    private String titulo;
    private BigDecimal nota; // Escala 0-20
}

