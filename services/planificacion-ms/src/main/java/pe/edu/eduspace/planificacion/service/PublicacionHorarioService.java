package pe.edu.eduspace.planificacion.service;

import java.util.List;
import pe.edu.eduspace.planificacion.dto.PublicacionResponse;

public interface PublicacionHorarioService {
    PublicacionResponse publicar(Long seccionId, Long usuarioId);

    List<PublicacionResponse> historial(Long seccionId);
}
