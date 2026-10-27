package pe.edu.upeu.eduspace.pago.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PagoAnuladoEvent {

    private Long pagoId;
    private Long alumnoId;
    private String motivo;
    private Long matriculaId;
    private String conceptoTipo;   // MATRICULA, PENSION, OTRO
    private java.math.BigDecimal monto;
}

