package pe.edu.eduspace.planificacion.service;

import java.util.List;
import pe.edu.eduspace.planificacion.dto.BloqueRequest;
import pe.edu.eduspace.planificacion.dto.BloqueResponse;
import pe.edu.eduspace.planificacion.entity.Turno;

public interface BloqueHorarioService {
    List<BloqueResponse> listar(Turno turno);

    BloqueResponse obtener(Long id);

    BloqueResponse crear(BloqueRequest request);

    BloqueResponse actualizar(Long id, BloqueRequest request);

    void eliminar(Long id);
}
