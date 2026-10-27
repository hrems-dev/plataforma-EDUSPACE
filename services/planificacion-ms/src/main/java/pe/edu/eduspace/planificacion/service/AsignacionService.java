package pe.edu.eduspace.planificacion.service;

import java.util.List;
import pe.edu.eduspace.planificacion.dto.AsignacionRequest;
import pe.edu.eduspace.planificacion.dto.AsignacionResponse;

public interface AsignacionService {
    List<AsignacionResponse> listarPorSeccion(Long seccionId);

    List<AsignacionResponse> listarPorDocente(Long docenteId);

    AsignacionResponse obtener(Long id);

    AsignacionResponse crear(AsignacionRequest request);

    AsignacionResponse cambiarDocente(Long id, Long docenteId);

    void eliminar(Long id);
}
