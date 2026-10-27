package pe.edu.upeu.eduspace.calificaciones.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReaperturaRequest {

    @NotNull(message = "seccionId es obligatorio")
    private Long seccionId;

    @NotNull(message = "periodoId es obligatorio")
    private Long periodoId;

    @NotBlank(message = "El motivo de reapertura es obligatorio para auditoría")
    @Size(max = 300, message = "El motivo no puede exceder 300 caracteres")
    private String motivo;
}

