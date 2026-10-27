package pe.edu.eduspace.alumnos.exception;

/** Recurso ya registrado (por ejemplo, documento de identidad repetido): HTTP 409. */
public class DuplicateResourceException extends RuntimeException {
    public DuplicateResourceException(String message) {
        super(message);
    }
}
