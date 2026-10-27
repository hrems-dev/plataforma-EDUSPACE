package pe.edu.upeu.eduspace.pago.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class TarifaResponse {
    private Long id;
    private Long anioId;
    private Long nivelId;
    private Long gradoId;
    private Long conceptoId;
    private String conceptoCodigo;
    private String conceptoNombre;
    private BigDecimal monto;
    private Short numCuotas;
    private Short mesInicio;
    private Short diaVencimiento;
    private Boolean activo;
}

