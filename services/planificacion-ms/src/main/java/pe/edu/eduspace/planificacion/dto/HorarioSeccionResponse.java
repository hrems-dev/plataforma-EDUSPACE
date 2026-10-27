package pe.edu.eduspace.planificacion.dto;

import java.util.List;
import pe.edu.eduspace.planificacion.entity.Turno;

/** Cuadrícula día × bloque de una sección: los bloques son las filas, cada clase se ubica por día y bloque. */
public record HorarioSeccionResponse(Long seccionId, String seccionNombre, Turno turno,
                                     List<BloqueResponse> bloques, List<HorarioResponse> clases) {
}
