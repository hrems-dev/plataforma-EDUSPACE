package pe.edu.eduspace.planificacion.service;

import pe.edu.eduspace.planificacion.dto.MallaGradoResponse;
import pe.edu.eduspace.planificacion.dto.MallaItemResponse;
import pe.edu.eduspace.planificacion.dto.MallaRequest;

public interface MallaService {
    MallaGradoResponse porGrado(Long gradoId);

    MallaItemResponse agregar(MallaRequest request);

    MallaItemResponse actualizarHoras(Long id, Short horasSemanales);

    void eliminar(Long id);
}
