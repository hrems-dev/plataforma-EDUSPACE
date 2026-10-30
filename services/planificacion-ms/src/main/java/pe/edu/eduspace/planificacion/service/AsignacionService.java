package pe.edu.eduspace.planificacion.service;

import java.util.List;
import pe.edu.eduspace.planificacion.dto.AsignacionRequest;
import pe.edu.eduspace.planificacion.dto.AsignacionResponse;

public interface AsignacionService {
    List<AsignacionResponse> listarPorSeccion(Long seccionId);

    List<AsignacionResponse> listarPorDocente(Long docenteId);

    /** Todas las asignaciones del año (una sola consulta en lugar de una por docente). */
    List<AsignacionResponse> listarPorAnio(Long anioId);

    AsignacionResponse obtener(Long id);

    AsignacionResponse crear(AsignacionRequest request);

    AsignacionResponse cambiarDocente(Long id, Long docenteId);

    void eliminar(Long id);
}
