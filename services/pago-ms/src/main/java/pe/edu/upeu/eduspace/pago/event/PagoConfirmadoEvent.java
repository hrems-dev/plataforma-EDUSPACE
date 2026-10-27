package pe.edu.upeu.eduspace.pago.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PagoConfirmadoEvent {

    private Long pagoId;
    private Long alumnoId;
    private Long matriculaId;
    private List<Long> cuotaIds;
    private String conceptoTipo; // MATRICULA, PENSION, OTRO
    private BigDecimal monto;
    private String concepto;
}

