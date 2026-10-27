package pe.edu.eduspace.planificacion.exception;

/** Violación de una regla de negocio: se traduce a HTTP 409. */
public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String message) {
        super(message);
    }
}
