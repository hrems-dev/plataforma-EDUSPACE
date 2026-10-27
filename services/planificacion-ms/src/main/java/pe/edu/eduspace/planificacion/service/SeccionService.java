package pe.edu.eduspace.planificacion.service;

import java.util.List;
import pe.edu.eduspace.planificacion.dto.SeccionRequest;
import pe.edu.eduspace.planificacion.dto.SeccionResponse;

public interface SeccionService {
    List<SeccionResponse> listar(Long anioId, Long gradoId);

    SeccionResponse obtener(Long id);

    SeccionResponse crear(SeccionRequest request);

    SeccionResponse actualizar(Long id, SeccionRequest request);

    void eliminar(Long id);
}
