package pe.edu.upeu.eduspace.pago.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AsignarDescuentoRequest {

    @NotNull(message = "El alumnoId es obligatorio")
    private Long alumnoId;

    @NotNull(message = "El descuentoId es obligatorio")
    private Long descuentoId;

    @NotNull(message = "El anioId es obligatorio")
    private Long anioId;

    @Size(max = 300)
    private String motivo;
}

