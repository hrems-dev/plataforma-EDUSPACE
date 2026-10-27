package pe.edu.upeu.eduspace.examenes.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamenPublicadoEvent {

    private Long examenId;
    private Long seccionId;
    private Long cursoId;
    private String cursoNombre;
    private String titulo;
    private LocalDateTime fechaInicio;
    private LocalDateTime fechaFin;
    private List<Long> alumnoIds;
}

