package pe.edu.upeu.eduspace.examenes.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class PreguntaRequest {

    @NotNull(message = "El cursoId es obligatorio")
    private Long cursoId;

    @Size(max = 100)
    private String tema;

    @NotBlank(message = "El tipo de pregunta es obligatorio")
    @Pattern(regexp = "OPCION_UNICA|OPCION_MULTIPLE|VERDADERO_FALSO|ABIERTA",
             message = "Tipo debe ser OPCION_UNICA, OPCION_MULTIPLE, VERDADERO_FALSO o ABIERTA")
    private String tipo;

    @NotBlank(message = "El enunciado es obligatorio")
    private String enunciado;

    @NotNull(message = "El puntaje es obligatorio")
    @DecimalMin(value = "0.25", message = "El puntaje debe ser mayor a 0")
    private BigDecimal puntaje = BigDecimal.ONE;

    private Boolean activa = true;

    @Valid
    private List<OpcionRequest> opciones;
}

