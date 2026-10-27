package pe.edu.upeu.eduspace.examenes.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class RevisionRequest {

    @NotNull(message = "El puntajeObtenido es obligatorio")
    @DecimalMin(value = "0.00", message = "El puntaje no puede ser negativo")
    private BigDecimal puntajeObtenido;
}

