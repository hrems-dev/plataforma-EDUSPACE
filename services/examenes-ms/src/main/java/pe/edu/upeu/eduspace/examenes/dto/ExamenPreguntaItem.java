package pe.edu.upeu.eduspace.examenes.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ExamenPreguntaItem {

    @NotNull(message = "El preguntaId es obligatorio")
    private Long preguntaId;

    @NotNull(message = "El orden es obligatorio")
    private Short orden;

    @NotNull(message = "El puntaje es obligatorio")
    @DecimalMin(value = "0.25", message = "El puntaje debe ser mayor a 0")
    private BigDecimal puntaje;
}

