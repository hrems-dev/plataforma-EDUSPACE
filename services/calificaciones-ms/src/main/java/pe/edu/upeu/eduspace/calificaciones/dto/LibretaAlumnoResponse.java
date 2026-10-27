package pe.edu.upeu.eduspace.calificaciones.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LibretaAlumnoResponse {

    private Long alumnoId;
    private String alumnoNombre;
    private Long matriculaId;
    private Long anioId;
    private Long nivelId;
    private Long gradoId;
    private Long seccionId;
    private List<CursoLibretaDto> cursos;
    private List<ResultadoAnualResponse> resultadosAnuales;
    private List<BoletinResponse> boletines;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CursoLibretaDto {
        private Long cursoId;
        private String cursoNombre;
        private Map<Long, PeriodoNotaDto> notasPorPeriodo; // periodoId -> { promedio, literal, estado }
        private BigDecimal promedioFinal;
        private String literalFinal;
        private String estadoFinal;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PeriodoNotaDto {
        private Long periodoId;
        private BigDecimal promedio;
        private String literal;
        private String estado; // BORRADOR, CERRADO
    }
}

