package pe.edu.eduspace.notificaciones.service;

import org.springframework.data.domain.Pageable;
import pe.edu.eduspace.notificaciones.dto.ConteoNoLeidasResponse;
import pe.edu.eduspace.notificaciones.dto.NotificacionResponse;
import pe.edu.eduspace.notificaciones.dto.PageResponse;

/** Bandeja del usuario autenticado: solo ve y modifica las suyas. */
public interface NotificacionService {
    PageResponse<NotificacionResponse> bandeja(Long usuarioId, Boolean leida, Pageable pageable);

    ConteoNoLeidasResponse conteoNoLeidas(Long usuarioId);

    NotificacionResponse marcarLeida(Long usuarioId, Long id);

    int marcarTodasLeidas(Long usuarioId);
}
