package pe.edu.eduspace.notificaciones.service;

import org.springframework.data.domain.Pageable;
import pe.edu.eduspace.notificaciones.dto.NotificacionAdminResponse;
import pe.edu.eduspace.notificaciones.dto.NotificacionEditarRequest;
import pe.edu.eduspace.notificaciones.dto.PageResponse;
import pe.edu.eduspace.notificaciones.entity.Canal;
import pe.edu.eduspace.notificaciones.entity.EstadoNotificacion;

/** Administración de las notificaciones ya generadas: consultar, corregir el texto y eliminar. */
public interface NotificacionAdminService {

    PageResponse<NotificacionAdminResponse> listar(String tipo, Canal canal, EstadoNotificacion estado, Long usuarioId, String texto,
                                                   Pageable pageable);

    NotificacionAdminResponse editar(Long id, NotificacionEditarRequest request);

    void eliminar(Long id);
}
