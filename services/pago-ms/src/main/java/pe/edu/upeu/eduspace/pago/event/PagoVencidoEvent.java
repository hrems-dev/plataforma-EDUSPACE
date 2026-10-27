package pe.edu.upeu.eduspace.pago.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PagoVencidoEvent {

    private Long cuotaId;
    private Long alumnoId;
    private Long matriculaId;
    private String concepto;
    private BigDecimal monto;
    private BigDecimal saldo;
    private LocalDate fechaVencimiento;
}

