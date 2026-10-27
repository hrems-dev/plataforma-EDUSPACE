package pe.edu.eduspace.notificaciones.service;

import java.util.List;
import pe.edu.eduspace.notificaciones.dto.PreferenciaRequest;
import pe.edu.eduspace.notificaciones.dto.PreferenciaResponse;
import pe.edu.eduspace.notificaciones.entity.Canal;

public interface PreferenciaService {
    /** Por defecto todo está habilitado: solo se guardan los cambios del usuario. */
    boolean habilitada(Long usuarioId, String tipo, Canal canal);

    List<PreferenciaResponse> listar(Long usuarioId);

    List<PreferenciaResponse> guardar(Long usuarioId, List<PreferenciaRequest> cambios);
}
