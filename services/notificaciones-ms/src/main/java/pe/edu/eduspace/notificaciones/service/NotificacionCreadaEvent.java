package pe.edu.eduspace.notificaciones.service;

/** Se publica al crear una notificación EMAIL o PUSH; el envío ocurre después del commit, fuera de la transacción. */
public record NotificacionCreadaEvent(Long notificacionId) {
}
