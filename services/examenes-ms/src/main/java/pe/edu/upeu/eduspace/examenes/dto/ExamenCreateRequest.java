package pe.edu.upeu.eduspace.examenes.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ExamenCreateRequest {

    @NotNull(message = "El anioId es obligatorio")
    private Long anioId;

    @NotNull(message = "El periodoId es obligatorio")
    private Long periodoId;

    @NotNull(message = "El seccionId es obligatorio")
    private Long seccionId;

    @NotNull(message = "El cursoId es obligatorio")
    private Long cursoId;

    @NotBlank(message = "El título es obligatorio")
    @Size(max = 200)
    private String titulo;

    private String instrucciones;

    @NotNull(message = "La fechaInicio es obligatoria")
    private LocalDateTime fechaInicio;

    @NotNull(message = "La fechaFin es obligatoria")
    private LocalDateTime fechaFin;

    @NotNull(message = "La duración en minutos es obligatoria")
    @Min(value = 5, message = "La duración mínima es de 5 minutos")
    @Max(value = 360, message = "La duración máxima es de 360 minutos")
    private Short duracionMin;

    @Min(1)
    private Short intentosMax = 1;

    private Boolean barajarPreguntas = false;

    @NotEmpty(message = "Debe incluir al menos una pregunta para armar el examen")
    @Valid
    private List<ExamenPreguntaItem> preguntas;
}

