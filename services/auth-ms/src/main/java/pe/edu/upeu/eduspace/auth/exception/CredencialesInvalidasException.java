package pe.edu.upeu.eduspace.auth.exception;

/** Usuario inexistente, clave errónea, usuario inactivo o refresh token inválido: siempre el mismo mensaje (401). */
public class CredencialesInvalidasException extends RuntimeException {
    public CredencialesInvalidasException(String mensaje) {
        super(mensaje);
    }
}
