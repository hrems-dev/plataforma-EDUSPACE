package pe.edu.eduspace.planificacion.service;

import java.util.List;
import pe.edu.eduspace.planificacion.dto.CursoRequest;
import pe.edu.eduspace.planificacion.dto.CursoResponse;

public interface CursoService {
    List<CursoResponse> listar();

    List<CursoResponse> porIds(List<Long> ids);

    CursoResponse obtener(Long id);

    CursoResponse crear(CursoRequest request);

    CursoResponse actualizar(Long id, CursoRequest request);

    void eliminar(Long id);
}
