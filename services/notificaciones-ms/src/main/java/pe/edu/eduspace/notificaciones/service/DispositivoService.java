package pe.edu.eduspace.notificaciones.service;

import java.util.List;
import pe.edu.eduspace.notificaciones.dto.DispositivoRequest;
import pe.edu.eduspace.notificaciones.dto.DispositivoResponse;

public interface DispositivoService {
    /** Registra el token; si ya existía en otro usuario, se reasigna (el dispositivo cambió de dueño). */
    DispositivoResponse registrar(Long usuarioId, DispositivoRequest request);

    void desactivar(Long usuarioId, String token);

    List<String> tokensActivos(Long usuarioId);
}
