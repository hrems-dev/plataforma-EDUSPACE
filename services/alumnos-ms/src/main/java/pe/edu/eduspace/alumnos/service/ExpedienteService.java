package pe.edu.eduspace.alumnos.service;

import pe.edu.eduspace.alumnos.dto.ExpedienteCompletoResponse;

public interface ExpedienteService {
    /** Completo si todos los documentos requeridos (configurables) existen y están verificados. */
    ExpedienteCompletoResponse evaluar(Long alumnoId);
}
