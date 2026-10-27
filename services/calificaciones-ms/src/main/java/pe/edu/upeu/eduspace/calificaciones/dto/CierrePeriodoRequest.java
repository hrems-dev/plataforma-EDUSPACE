package pe.edu.upeu.eduspace.calificaciones.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CierrePeriodoRequest {

    @NotNull(message = "seccionId es obligatorio")
    private Long seccionId;

    @NotNull(message = "periodoId es obligatorio")
    private Long periodoId;
}

