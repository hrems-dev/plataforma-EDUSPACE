package pe.edu.eduspace.planificacion.service;

import java.util.List;
import pe.edu.eduspace.planificacion.dto.CatalogoUpdateRequest;
import pe.edu.eduspace.planificacion.dto.GradoResponse;

public interface GradoService {
    List<GradoResponse> listar(Long nivelId);

    GradoResponse obtener(Long id);

    GradoResponse actualizar(Long id, CatalogoUpdateRequest request);
}
