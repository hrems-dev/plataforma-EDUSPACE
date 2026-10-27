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
public class RegistroNotasSeccionResponse {

    private Long seccionId;
    private Long cursoId;
    private Long periodoId;
    private String estadoPeriodo; // ABIERTO, CERRADO, REABIERTO
    private List<CriterioResponse> criterios;
    private List<FilaRegistroAlumno> alumnos;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FilaRegistroAlumno {
        private Long matriculaId;
        private Long alumnoId;
        private String alumnoNombre;
        private Map<Long, BigDecimal> notasPorCriterioId; // criterioId -> valor
        private BigDecimal promedio;
        private String literal;
    }
}

