package pe.edu.upeu.eduspace.asistencia.event;

import lombok.Data;
import lombok.NoArgsConstructor;

/** Copia propia del contrato matricula.* (solo lo que asistencia necesita). */
@Data
@NoArgsConstructor
public class MatriculaEvent {
    private Long matriculaId;
    private Long alumnoId;
    private String alumnoNombre;
    private Long anioId;
    private Long nivelId;
    private Long gradoId;
    private Long seccionId;
    private String estado;
}
