package pe.edu.upeu.eduspace.calificaciones.util;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Generador mínimo de PDF de texto (Courier, A4, varias páginas) sin dependencias externas. */
public final class PdfTexto {

    private static final int LINEAS_POR_PAGINA = 60;

    private PdfTexto() {}

    public static byte[] generar(String titulo, String contenido) {
        List<String> lineas = new ArrayList<>();
        lineas.add(titulo);
        lineas.add("");
        for (String l : contenido.split("\\R", -1)) {
            lineas.add(l);
        }
        List<List<String>> paginas = new ArrayList<>();
        for (int i = 0; i < lineas.size(); i += LINEAS_POR_PAGINA) {
            paginas.add(lineas.subList(i, Math.min(i + LINEAS_POR_PAGINA, lineas.size())));
        }
        int n = paginas.size();
        // Objetos: 1 catálogo, 2 páginas, 3 fuente, luego (página, contenido) por cada página
        List<byte[]> objs = new ArrayList<>();
        objs.add(bytes("<< /Type /Catalog /Pages 2 0 R >>"));
        StringBuilder kids = new StringBuilder();
        for (int i = 0; i < n; i++) kids.append(4 + 2 * i).append(" 0 R ");
        objs.add(bytes("<< /Type /Pages /Kids [" + kids + "] /Count " + n + " >>"));
        objs.add(bytes("<< /Type /Font /Subtype /Type1 /BaseFont /Courier /Encoding /WinAnsiEncoding >>"));
        for (int i = 0; i < n; i++) {
            objs.add(bytes("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 3 0 R >> >> /Contents "
                    + (5 + 2 * i) + " 0 R >>"));
            StringBuilder s = new StringBuilder("BT /F1 10 Tf 12 TL 40 800 Td\n");
            for (String l : paginas.get(i)) {
                s.append('(').append(escapar(l)).append(") '\n");
            }
            s.append("ET");
            byte[] flujo = s.toString().getBytes(StandardCharsets.ISO_8859_1);
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            o.writeBytes(bytes("<< /Length " + flujo.length + " >>\nstream\n"));
            o.writeBytes(flujo);
            o.writeBytes(bytes("\nendstream"));
            objs.add(o.toByteArray());
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(bytes("%PDF-1.4\n"));
        int[] pos = new int[objs.size()];
        for (int i = 0; i < objs.size(); i++) {
            pos[i] = out.size();
            out.writeBytes(bytes((i + 1) + " 0 obj\n"));
            out.writeBytes(objs.get(i));
            out.writeBytes(bytes("\nendobj\n"));
        }
        int xref = out.size();
        StringBuilder x = new StringBuilder("xref\n0 " + (objs.size() + 1) + "\n0000000000 65535 f \n");
        for (int p : pos) x.append(String.format("%010d 00000 n \n", p));
        x.append("trailer\n<< /Size ").append(objs.size() + 1).append(" /Root 1 0 R >>\nstartxref\n").append(xref).append("\n%%EOF\n");
        out.writeBytes(bytes(x.toString()));
        return out.toByteArray();
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.ISO_8859_1);
    }

    private static String escapar(String l) {
        StringBuilder sb = new StringBuilder();
        for (char c : l.toCharArray()) {
            if (c == '(' || c == ')' || c == '\\') sb.append('\\').append(c);
            else if (c > 255) sb.append('?');
            else sb.append(c);
        }
        return sb.toString();
    }
}
