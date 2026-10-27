package pe.edu.eduspace.notificaciones.service;

/** A quién va una notificación de un evento. */
public enum Destino {
    /** El alumno (si tiene cuenta) y todos sus apoderados con cuenta. */
    ALUMNO_Y_APODERADOS,
    /** El responsable de pago; si no hay, el apoderado principal; si no hay, todos los apoderados. */
    RESPONSABLE_PAGO,
    /** Todos los apoderados con cuenta (no el alumno). */
    APODERADOS,
    /** Usuarios ya indicados en el evento (docentes del horario), sin consultar a alumnos-ms. */
    USUARIOS_DIRECTOS
}
