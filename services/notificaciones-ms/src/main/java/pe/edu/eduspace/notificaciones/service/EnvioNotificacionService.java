package pe.edu.eduspace.notificaciones.service;

/** Un intento de entrega de una notificación pendiente o fallida, en su propia transacción. */
public interface EnvioNotificacionService {
    void intentar(Long notificacionId);
}
