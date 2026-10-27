package pe.edu.upeu.eduspace.pago.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class RegistrarPagoRequest {

    @NotNull(message = "El alumnoId es obligatorio")
    private Long alumnoId;

    @Size(max = 200)
    private String pagadorNombre;

    @Size(max = 20)
    private String pagadorDocumento;

    @NotNull(message = "El montoTotal es obligatorio")
    @DecimalMin(value = "0.01", message = "El monto debe ser mayor a 0")
    private BigDecimal montoTotal;

    @NotBlank(message = "El medio de pago es obligatorio")
    @Pattern(regexp = "EFECTIVO|TARJETA|YAPE_PLIN|TRANSFERENCIA|ONLINE_SIMULADO",
             message = "Medio debe ser EFECTIVO, TARJETA, YAPE_PLIN, TRANSFERENCIA u ONLINE_SIMULADO")
    private String medio;

    @Size(max = 80)
    private String referencia;

    @NotBlank(message = "idempotencyKey es obligatoria para evitar cobros duplicados")
    @Size(max = 80)
    private String idempotencyKey;

    @Pattern(regexp = "RECIBO|BOLETA", message = "tipoComprobante debe ser RECIBO o BOLETA")
    private String tipoComprobante = "RECIBO";

    @NotEmpty(message = "Debe incluir al menos una cuota a pagar")
    @Valid
    private List<PagoDetalleRequest> detalles;
}

