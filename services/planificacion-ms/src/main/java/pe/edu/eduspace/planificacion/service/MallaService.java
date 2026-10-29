package pe.edu.eduspace.planificacion.service;

import pe.edu.eduspace.planificacion.dto.MallaGradoResponse;
import pe.edu.eduspace.planificacion.dto.MallaItemResponse;
import pe.edu.eduspace.planificacion.dto.MallaLoteRequest;
import pe.edu.eduspace.planificacion.dto.MallaLoteResponse;
import pe.edu.eduspace.planificacion.dto.MallaRequest;

public interface MallaService {
    MallaGradoResponse porGrado(Long gradoId);

    MallaItemResponse agregar(MallaRequest request);

    MallaItemResponse actualizarHoras(Long id, Short horasSemanales);

    /** Agrega o actualiza varios cursos en la malla de varios grados. */
    MallaLoteResponse agregarLote(MallaLoteRequest request);

    void eliminar(Long id);
}
