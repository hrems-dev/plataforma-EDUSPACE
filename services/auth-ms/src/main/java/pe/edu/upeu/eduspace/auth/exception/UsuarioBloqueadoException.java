package pe.edu.upeu.eduspace.auth.exception;

/** Demasiados intentos fallidos (423). */
public class UsuarioBloqueadoException extends RuntimeException {
    public UsuarioBloqueadoException(String mensaje) {
        super(mensaje);
    }
}
