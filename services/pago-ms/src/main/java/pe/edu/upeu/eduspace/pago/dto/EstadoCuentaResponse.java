package pe.edu.upeu.eduspace.pago.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class EstadoCuentaResponse {
    private Long alumnoId;
    private String alumnoNombre;
    private String responsableNombre;
    private BigDecimal totalCobrado;
    private BigDecimal totalPendiente;
    private BigDecimal totalVencido;
    private List<CuotaResponse> cuotas;
}

