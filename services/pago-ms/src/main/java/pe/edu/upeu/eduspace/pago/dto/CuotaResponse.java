package pe.edu.upeu.eduspace.pago.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class CuotaResponse {
    private Long id;
    private Long matriculaId;
    private Long alumnoId;
    private Long anioId;
    private Long conceptoId;
    private String conceptoNombre;
    private String conceptoTipo;
    private String descripcion;
    private Short numeroCuota;
    private BigDecimal montoBase;
    private BigDecimal descuento;
    private BigDecimal mora;
    private BigDecimal montoTotal;
    private BigDecimal saldo;
    private LocalDate fechaVencimiento;
    private String estado;
    private long diasAtraso;
    private Boolean avisoEnviado;
}

