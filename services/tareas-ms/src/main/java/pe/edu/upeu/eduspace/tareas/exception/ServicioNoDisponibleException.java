package pe.edu.upeu.eduspace.tareas.exception;

/** Un servicio del que depende la operación no responde (se mapea a 503). */
public class ServicioNoDisponibleException extends RuntimeException {
    public ServicioNoDisponibleException(String mensaje) {
        super(mensaje);
    }
}
