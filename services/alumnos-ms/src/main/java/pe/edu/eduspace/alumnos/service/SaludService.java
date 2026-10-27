package pe.edu.eduspace.alumnos.service;

import pe.edu.eduspace.alumnos.dto.SaludRequest;
import pe.edu.eduspace.alumnos.dto.SaludResponse;

/** Dato sensible de menores: cada consulta y cada cambio quedan en el log con el usuario que los hizo. */
public interface SaludService {
    SaludResponse obtener(Long alumnoId, Long usuarioId);

    SaludResponse guardar(Long alumnoId, SaludRequest request, Long usuarioId);
}
