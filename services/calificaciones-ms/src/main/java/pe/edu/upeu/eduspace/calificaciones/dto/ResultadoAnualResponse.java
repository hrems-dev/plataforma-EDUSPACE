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
public class ResultadoAnualResponse {

    private Long id;
    private Long matriculaId;
    private Long alumnoId;
    private Long anioId;
    private Long cursoId;
    private BigDecimal promedioFinal;
    private String literal;
    private String estado;
    private LocalDateTime calculadoEn;
}

