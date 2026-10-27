package pe.edu.upeu.eduspace.calificaciones.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotaRequest {

    @NotNull(message = "matriculaId es obligatorio")
    private Long matriculaId;

    @NotNull(message = "alumnoId es obligatorio")
    private Long alumnoId;

    @NotNull(message = "seccionId es obligatorio")
    private Long seccionId;

    @NotNull(message = "cursoId es obligatorio")
    private Long cursoId;

    @NotNull(message = "periodoId es obligatorio")
    private Long periodoId;

    @NotNull(message = "criterioId es obligatorio")
    private Long criterioId;

    @NotNull(message = "El valor de la nota es obligatorio")
    @DecimalMin(value = "0.00", message = "La nota mínima es 0")
    @DecimalMax(value = "20.00", message = "La nota máxima es 20")
    private BigDecimal valor;

    @Pattern(regexp = "MANUAL|TAREA|EXAMEN", message = "origen debe ser MANUAL, TAREA o EXAMEN")
    @Builder.Default
    private String origen = "MANUAL";

    private Long origenId;
}

