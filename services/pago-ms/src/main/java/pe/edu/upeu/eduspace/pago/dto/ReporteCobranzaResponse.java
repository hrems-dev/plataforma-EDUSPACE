package pe.edu.upeu.eduspace.pago.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class ReporteCobranzaResponse {
    private LocalDateTime desde;
    private LocalDateTime hasta;
    private BigDecimal totalCobrado;
    private long totalTransacciones;
    private Map<String, BigDecimal> totalPorMedio;
    private List<PagoResponse> pagos;
}

