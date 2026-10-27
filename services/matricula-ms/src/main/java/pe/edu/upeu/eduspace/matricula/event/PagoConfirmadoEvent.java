package pe.edu.upeu.eduspace.matricula.event;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Copia propia del contrato pago.confirmado (solo lo que matrícula necesita). */
@Data
@NoArgsConstructor
public class PagoConfirmadoEvent {
    private Long pagoId;
    private Long alumnoId;
    private Long matriculaId;
    private String conceptoTipo;
    private BigDecimal monto;
}
