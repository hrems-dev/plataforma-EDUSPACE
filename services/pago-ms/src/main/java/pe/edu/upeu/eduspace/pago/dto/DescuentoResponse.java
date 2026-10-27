package pe.edu.upeu.eduspace.pago.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class DescuentoResponse {
    private Long id;
    private String nombre;
    private String tipo;
    private BigDecimal valor;
    private String aplicaA;
    private Boolean activo;
}

