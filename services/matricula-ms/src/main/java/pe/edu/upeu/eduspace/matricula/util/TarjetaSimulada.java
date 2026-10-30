package pe.edu.upeu.eduspace.matricula.util;

import java.time.YearMonth;

/**
 * Pasarela de pago FALSA para la matrícula en línea: no cobra nada. Valida el formato (número con algoritmo de Luhn, vencimiento
 * vigente, CVV) y aprueba, salvo la tarjeta de prueba que termina en 0002, que se rechaza para poder ver el caso de error.
 * Los datos de la tarjeta no se guardan ni se escriben en el registro: solo se conserva la marca y los últimos 4 dígitos.
 */
public final class TarjetaSimulada {

    public record Resultado(boolean aprobada, String mensaje, String referencia) {}

    private TarjetaSimulada() {}

    public static Resultado procesar(String numero, String vencimiento, String cvv, YearMonth hoy) {
        String n = numero == null ? "" : numero.replaceAll("[\\s-]", "");
        if (!n.matches("\\d{13,19}") || !luhn(n)) return new Resultado(false, "El número de tarjeta no es válido.", null);
        if (cvv == null || !cvv.matches("\\d{3,4}")) return new Resultado(false, "El código de seguridad (CVV) no es válido.", null);
        YearMonth v = parsear(vencimiento);
        if (v == null) return new Resultado(false, "El vencimiento debe tener el formato MM/AA.", null);
        if (v.isBefore(hoy)) return new Resultado(false, "La tarjeta está vencida.", null);
        String ref = marca(n) + " ****" + n.substring(n.length() - 4);
        if (n.endsWith("0002")) return new Resultado(false, "La tarjeta fue rechazada por el banco (tarjeta de prueba de rechazo).", ref);
        return new Resultado(true, "Pago aprobado (simulado).", ref);
    }

    static boolean luhn(String n) {
        int suma = 0;
        boolean doble = false;
        for (int i = n.length() - 1; i >= 0; i--) {
            int d = n.charAt(i) - '0';
            if (doble) {
                d *= 2;
                if (d > 9) d -= 9;
            }
            suma += d;
            doble = !doble;
        }
        return suma % 10 == 0;
    }

    static String marca(String n) {
        return n.startsWith("4") ? "VISA" : n.matches("^5[1-5].*") ? "MASTERCARD" : n.matches("^3[47].*") ? "AMEX" : "TARJETA";
    }

    static YearMonth parsear(String v) {
        if (v == null || !v.matches("\\d{2}\\s*/\\s*\\d{2,4}")) return null;
        String[] p = v.split("\\s*/\\s*");
        int mes = Integer.parseInt(p[0]);
        int anio = Integer.parseInt(p[1]);
        if (mes < 1 || mes > 12) return null;
        return YearMonth.of(anio < 100 ? 2000 + anio : anio, mes);
    }
}
