package pe.edu.eduspace.alumnos.exception;

/** Datos de entrada que pasan la validación de campos pero son incoherentes: HTTP 400 con el motivo exacto. */
public class DatosInvalidosException extends RuntimeException {
    public DatosInvalidosException(String message) {
        super(message);
    }
}
