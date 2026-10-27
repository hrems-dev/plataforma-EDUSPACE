package pe.edu.upeu.eduspace.calificaciones.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidarPesosResponse {

    private Long periodoId;
    private Long seccionId;
    private Long cursoId;
    private BigDecimal sumaPesos;
    private boolean esValido; // true si suma == 100.00
    private String mensaje;
    private List<CriterioResponse> criterios;
}

