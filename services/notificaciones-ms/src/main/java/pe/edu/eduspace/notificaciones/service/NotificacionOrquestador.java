package pe.edu.eduspace.notificaciones.service;

/** Convierte un evento de otro servicio en notificaciones para los usuarios correctos. */
public interface NotificacionOrquestador {

    /**
     * Procesa el evento en una sola transacción: descarta los repetidos por {@code eventId}, resuelve destinatarios,
     * respeta preferencias y crea las notificaciones por canal. Si alumnos-ms no responde deja una notificación
     * PENDIENTE por alumno para reintentar.
     */
    void procesar(EventoNotificable evento);

    /** Reintento de un alumno que quedó pendiente: no crea otra pendiente si sigue sin responder. */
    ResultadoResolucion reintentarPendiente(EventoNotificable evento, Long alumnoId);
}
