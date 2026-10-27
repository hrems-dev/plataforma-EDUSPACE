package pe.edu.eduspace.planificacion.service;

import java.util.List;
import pe.edu.eduspace.planificacion.dto.CatalogoUpdateRequest;
import pe.edu.eduspace.planificacion.dto.NivelResponse;

public interface NivelService {
    List<NivelResponse> listar();

    NivelResponse obtener(Long id);

    NivelResponse actualizar(Long id, CatalogoUpdateRequest request);
}
