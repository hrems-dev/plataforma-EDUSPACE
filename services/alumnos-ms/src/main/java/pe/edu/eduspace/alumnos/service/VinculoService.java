package pe.edu.eduspace.alumnos.service;

import java.util.List;
import pe.edu.eduspace.alumnos.dto.VinculoApoderadoRequest;
import pe.edu.eduspace.alumnos.dto.VinculoResponse;
import pe.edu.eduspace.alumnos.dto.VinculoUpdateRequest;

public interface VinculoService {
    List<VinculoResponse> listar(Long alumnoId);

    VinculoResponse agregar(Long alumnoId, VinculoApoderadoRequest request);

    VinculoResponse actualizar(Long alumnoId, Long vinculoId, VinculoUpdateRequest request);

    void quitar(Long alumnoId, Long vinculoId);
}
