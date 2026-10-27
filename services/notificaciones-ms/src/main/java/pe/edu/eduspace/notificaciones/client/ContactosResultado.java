package pe.edu.eduspace.notificaciones.client;

/** Resultado de pedir los contactos de un alumno: datos, "alumnos-ms no disponible" o "el alumno no existe". */
public record ContactosResultado(Estado estado, ContactosDto datos) {

    public enum Estado { DISPONIBLE, NO_DISPONIBLE, NO_EXISTE }

    public static ContactosResultado disponible(ContactosDto datos) {
        return new ContactosResultado(Estado.DISPONIBLE, datos);
    }

    public static ContactosResultado noDisponible() {
        return new ContactosResultado(Estado.NO_DISPONIBLE, null);
    }

    public static ContactosResultado noExiste() {
        return new ContactosResultado(Estado.NO_EXISTE, null);
    }
}
