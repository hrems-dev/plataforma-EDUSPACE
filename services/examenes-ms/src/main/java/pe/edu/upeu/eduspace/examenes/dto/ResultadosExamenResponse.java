package pe.edu.upeu.eduspace.examenes.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class ResultadosExamenResponse {
    private Long examenId;
    private String titulo;
    private BigDecimal puntajeTotal;
    private int totalIntentos;
    private int alumnosEvaluados;
    private BigDecimal promedioNotas;
    private BigDecimal notaMaxima;
    private BigDecimal notaMinima;
    private List<IntentoEstudianteResumen> intentos;

    @Data
    @Builder
    public static class IntentoEstudianteResumen {
        private Long intentoId;
        private Long alumnoId;
        private String alumnoNombre;
        private Short numeroIntento;
        private String estado;
        private BigDecimal puntajeObtenido;
        private BigDecimal nota;
        private boolean requiereRevision;
    }
}

