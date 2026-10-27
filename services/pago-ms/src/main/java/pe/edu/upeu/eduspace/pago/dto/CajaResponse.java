package pe.edu.upeu.eduspace.pago.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class CajaResponse {
    private Long id;
    private Long cajeroUsuarioId;
    private LocalDateTime aperturaEn;
    private BigDecimal montoApertura;
    private LocalDateTime cierreEn;
    private BigDecimal montoCierreDeclarado;
    private BigDecimal montoCierreSistema;
    private BigDecimal diferencia;
    private String estado;
}

