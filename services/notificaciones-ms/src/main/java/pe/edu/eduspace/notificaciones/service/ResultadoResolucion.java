package pe.edu.eduspace.notificaciones.service;

/** Qué pasó al intentar resolver los destinatarios de un alumno. */
public enum ResultadoResolucion {
    /** Se crearon las notificaciones. */
    RESUELTO,
    /** alumnos-ms no respondió: queda una notificación PENDIENTE para reintentar. */
    PENDIENTE,
    /** El alumno no existe en alumnos-ms: el evento se descarta. */
    NO_EXISTE
}
