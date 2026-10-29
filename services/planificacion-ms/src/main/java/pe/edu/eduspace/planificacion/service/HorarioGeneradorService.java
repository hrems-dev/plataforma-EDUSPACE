package pe.edu.eduspace.planificacion.service;

import java.util.List;
import pe.edu.eduspace.planificacion.dto.GenerarHorarioRequest;
import pe.edu.eduspace.planificacion.dto.PreparacionSeccionResponse;
import pe.edu.eduspace.planificacion.dto.ResultadoGeneracion;

/** Armado automático de horarios a partir de la malla, los docentes asignados y los bloques de cada turno. */
public interface HorarioGeneradorService {

    /** Estado de cada sección del año antes de generar: qué tiene y qué le falta. */
    List<PreparacionSeccionResponse> preparacion(Long anioId);

    ResultadoGeneracion generar(GenerarHorarioRequest request);
}
