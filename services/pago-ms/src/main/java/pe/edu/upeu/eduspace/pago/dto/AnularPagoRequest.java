package pe.edu.upeu.eduspace.pago.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AnularPagoRequest {

    @NotBlank(message = "El motivo de anulación es obligatorio")
    @Size(max = 300)
    private String motivo;
}

