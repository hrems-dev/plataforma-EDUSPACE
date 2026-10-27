package pe.edu.upeu.eduspace.pago.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class PagoResponse {
    private Long id;
    private Long alumnoId;
    private String pagadorNombre;
    private String pagadorDocumento;
    private BigDecimal montoTotal;
    private String medio;
    private String referencia;
    private String estado;
    private Long cajaSesionId;
    private Long registradoPor;
    private LocalDateTime pagadoEn;
    private String idempotencyKey;
    private ComprobanteResponse comprobante;
    private List<PagoDetalleResponse> detalles;

    @Data
    @Builder
    public static class PagoDetalleResponse {
        private Long id;
        private Long cuotaId;
        private String cuotaDescripcion;
        private BigDecimal montoAplicado;
        private BigDecimal nuevoSaldoCuota;
        private String nuevoEstadoCuota;
    }
}

