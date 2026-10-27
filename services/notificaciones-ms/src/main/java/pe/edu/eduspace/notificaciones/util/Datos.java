package pe.edu.eduspace.notificaciones.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Armado de los datos que rellenan los marcadores de las plantillas. */
public final class Datos {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private Datos() {
    }

    /** Pares clave/valor alternados; los valores nulos o vacíos se omiten (la plantilla pondrá un texto neutro). */
    public static Map<String, String> de(Object... pares) {
        Map<String, String> datos = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pares.length; i += 2) {
            Object valor = pares[i + 1];
            if (valor != null && !String.valueOf(valor).isBlank()) {
                datos.put(String.valueOf(pares[i]), String.valueOf(valor));
            }
        }
        return datos;
    }

    /** Dinero con dos decimales: 150 → "150.00". */
    public static String dinero(BigDecimal monto) {
        return monto == null ? null : monto.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /** Nota sin ceros sobrantes: 17.50 → "17.5"; 18.00 → "18". */
    public static String nota(BigDecimal nota) {
        return nota == null ? null : nota.stripTrailingZeros().toPlainString();
    }

    /**
     * Fecha legible dd/MM/yyyy. Acepta texto ISO (con o sin hora) y el arreglo {@code [aaaa, mm, dd, ...]} que
     * producen algunos serializadores.
     */
    public static String fecha(Object valor) {
        if (valor == null) {
            return null;
        }
        try {
            if (valor instanceof List<?> partes && partes.size() >= 3) {
                return LocalDate.of(((Number) partes.get(0)).intValue(), ((Number) partes.get(1)).intValue(),
                        ((Number) partes.get(2)).intValue()).format(FECHA);
            }
            String texto = String.valueOf(valor);
            return texto.length() >= 10 ? LocalDate.parse(texto.substring(0, 10)).format(FECHA) : texto;
        } catch (RuntimeException ex) {
            return String.valueOf(valor);
        }
    }
}
