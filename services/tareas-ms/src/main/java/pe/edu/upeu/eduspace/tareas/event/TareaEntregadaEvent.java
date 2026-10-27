package pe.edu.upeu.eduspace.tareas.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TareaEntregadaEvent {
    private Long tareaId;
    private Long entregaId;
    private Long alumnoId;
    private Long matriculaId;
    private Long seccionId;
    private Short version;
    private Boolean esTardia;
}
