package pe.edu.eduspace.alumnos.util;

import java.util.regex.Pattern;
import pe.edu.eduspace.alumnos.exception.DatosInvalidosException;

/** Validación del número de documento según su tipo. */
public final class DocumentoIdentidad {

    private static final Pattern DNI = Pattern.compile("\\d{8}");
    private static final Pattern CE = Pattern.compile("[A-Za-z0-9]{9,12}");
    private static final Pattern PASAPORTE = Pattern.compile("[A-Za-z0-9]{6,12}");

    private DocumentoIdentidad() {
    }

    /** Devuelve el tipo normalizado (DNI por defecto) o lanza {@link DatosInvalidosException}. */
    public static String validar(String tipo, String numero) {
        String t = tipo == null || tipo.isBlank() ? "DNI" : tipo.trim().toUpperCase();
        String n = numero == null ? "" : numero.trim();
        boolean ok = switch (t) {
            case "DNI" -> DNI.matcher(n).matches();
            case "CE" -> CE.matcher(n).matches();
            case "PASAPORTE" -> PASAPORTE.matcher(n).matches();
            default -> throw new DatosInvalidosException("Tipo de documento no válido: " + t + " (use DNI, CE o PASAPORTE)");
        };
        if (!ok) {
            throw new DatosInvalidosException("El número de documento no tiene el formato de un " + t);
        }
        return t;
    }
}
