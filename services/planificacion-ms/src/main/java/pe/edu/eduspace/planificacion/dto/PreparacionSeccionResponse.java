package pe.edu.eduspace.planificacion.dto;

import java.util.List;
import pe.edu.eduspace.planificacion.entity.Turno;

/** Qué le falta a una sección para poder armar su horario. {@code lista} = sin problemas pendientes. */
public record PreparacionSeccionResponse(Long seccionId, String nombre, Long gradoId, String gradoNombre, Long nivelId, String nivelNombre,
                                         Turno turno, String aula, int cursosMalla, int horasMalla, int espacios, List<String> sinDocente,
                                         int clasesProgramadas, boolean lista, List<String> problemas) {
}
