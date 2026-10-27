package pe.edu.upeu.eduspace.pago.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class ComprobanteResponse {
    private Long id;
    private Long pagoId;
    private String tipo;
    private String serie;
    private Long numero;
    private String numeroCompleto; // Ej: R001-00000042
    private BigDecimal total;
    private String estado;
    private String rutaPdf;
    private LocalDateTime emitidoEn;
}

