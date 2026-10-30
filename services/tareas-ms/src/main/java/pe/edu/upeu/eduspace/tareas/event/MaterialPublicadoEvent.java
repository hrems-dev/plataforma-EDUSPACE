package pe.edu.upeu.eduspace.tareas.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Un docente publicó material de un curso: se avisa a los alumnos de la sección y a sus apoderados. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaterialPublicadoEvent {
    private Long materialId;
    private Long seccionId;
    private Long cursoId;
    private String cursoNombre;
    private String titulo;
    private List<Long> alumnoIds;
}
