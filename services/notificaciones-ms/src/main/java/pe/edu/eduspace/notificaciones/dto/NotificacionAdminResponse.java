package pe.edu.eduspace.notificaciones.dto;

import java.time.LocalDateTime;
import pe.edu.eduspace.notificaciones.entity.Canal;
import pe.edu.eduspace.notificaciones.entity.EstadoNotificacion;

/** Una notificación vista por el ADMIN (cualquier destinatario, cualquier canal). */
public record NotificacionAdminResponse(Long id, Long usuarioId, String tipo, Canal canal, EstadoNotificacion estado, String titulo,
                                        String mensaje, Boolean leida, LocalDateTime creadaEn, LocalDateTime enviadaEn) {
}
