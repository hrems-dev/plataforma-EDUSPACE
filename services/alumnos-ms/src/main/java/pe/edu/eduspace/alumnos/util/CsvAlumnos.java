package pe.edu.eduspace.alumnos.util;

import java.util.ArrayList;
import java.util.List;

/** Lector CSV mínimo (comillas dobles, delimitador {@code ,} o {@code ;} detectado en el encabezado). */
public final class CsvAlumnos {

    private CsvAlumnos() {
    }

    public static char detectarDelimitador(String encabezado) {
        long comas = encabezado.chars().filter(c -> c == ',').count();
        long puntos = encabezado.chars().filter(c -> c == ';').count();
        return puntos > comas ? ';' : ',';
    }

    public static List<String> dividir(String linea, char delimitador) {
        List<String> campos = new ArrayList<>();
        StringBuilder actual = new StringBuilder();
        boolean entreComillas = false;
        for (int i = 0; i < linea.length(); i++) {
            char c = linea.charAt(i);
            if (entreComillas) {
                if (c == '"' && i + 1 < linea.length() && linea.charAt(i + 1) == '"') {
                    actual.append('"');
                    i++;
                } else if (c == '"') {
                    entreComillas = false;
                } else {
                    actual.append(c);
                }
            } else if (c == '"') {
                entreComillas = true;
            } else if (c == delimitador) {
                campos.add(actual.toString().trim());
                actual.setLength(0);
            } else {
                actual.append(c);
            }
        }
        campos.add(actual.toString().trim());
        return campos;
    }
}
