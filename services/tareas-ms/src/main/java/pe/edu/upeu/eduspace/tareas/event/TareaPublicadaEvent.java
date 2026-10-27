package pe.edu.upeu.eduspace.tareas.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/** También se usa para tarea.recordatorio (mismos datos, alumnoIds = quienes no han entregado). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TareaPublicadaEvent {
    private Long tareaId;
    private Long seccionId;
    private Long cursoId;
    private String cursoNombre;
    private String titulo;
    private LocalDateTime fechaEntrega;
    private List<Long> alumnoIds;
}
