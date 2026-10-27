package pe.edu.upeu.eduspace.pago.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
public class CierreCajaResponse {
    private Long cajaId;
    private Long cajeroUsuarioId;
    private LocalDateTime aperturaEn;
    private LocalDateTime cierreEn;
    private BigDecimal montoApertura;
    private BigDecimal cobrosEfectivo;
    private BigDecimal cobrosOtrosMedios;
    private BigDecimal totalCobrado;
    private BigDecimal efectivoEsperado;
    private BigDecimal montoDeclarado;
    private BigDecimal diferencia;
    private Map<String, BigDecimal> totalesPorMedio;
}

