package pe.edu.eduspace.planificacion.service;

import java.util.List;
import pe.edu.eduspace.planificacion.dto.AulaRequest;
import pe.edu.eduspace.planificacion.dto.AulaResponse;
import pe.edu.eduspace.planificacion.entity.TipoAula;

public interface AulaService {
    List<AulaResponse> listar(TipoAula tipo);

    AulaResponse obtener(Long id);

    AulaResponse crear(AulaRequest request);

    AulaResponse actualizar(Long id, AulaRequest request);

    void eliminar(Long id);
}
