package pe.edu.upeu.eduspace.matricula.util;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Constancia de matrícula en PDF (A4) sin dependencias externas: encabezado de color, datos del alumno y del apoderado,
 * pago de la matrícula, calendario de pensiones, horario semanal y datos de acceso al sistema.
 */
public final class PdfConstancia {

    public record Fila(String a, String b, String c) {}

    public record FilaHorario(String hora, String[] dias) {}

    public record Datos(String colegio, String anio, String codigo, String emitidaEn, String alumno, String alumnoDoc, String grado,
                        String seccion, String turno, String apoderado, String apoderadoDoc, String contacto, String montoMatricula,
                        String comprobante, String medioPago, String pagadoEn, List<Fila> pensiones, String totalPensiones,
                        List<FilaHorario> horario, String usuarioAlumno, String usuarioApoderado) {}

    private static final double[] AZUL = {0.12, 0.23, 0.53};
    private static final double[] GRIS = {0.35, 0.38, 0.45};
    private static final double[] SUAVE = {0.93, 0.95, 0.99};
    private static final double[] NEGRO = {0.08, 0.1, 0.16};
    private static final double[] BLANCO = {1, 1, 1};
    private static final String[] DIAS = {"Lunes", "Martes", "Miércoles", "Jueves", "Viernes"};

    private final List<StringBuilder> paginas = new ArrayList<>();
    private StringBuilder pag;
    private double y;

    private PdfConstancia() {
        nuevaPagina();
    }

    public static byte[] generar(Datos d) {
        PdfConstancia p = new PdfConstancia();
        p.encabezado(d);
        p.seccion("Datos de la matrícula");
        p.par("Código", d.codigo(), "Año escolar", d.anio());
        p.par("Estado", "MATRICULADO", "Emitida el", d.emitidaEn());
        p.seccion("Alumno");
        p.par("Nombre", d.alumno(), "Documento", d.alumnoDoc());
        p.par("Grado", d.grado(), "Sección y turno", d.seccion() + " · " + d.turno());
        p.seccion("Apoderado");
        p.par("Nombre", d.apoderado(), "Documento", d.apoderadoDoc());
        p.par("Correo", d.contacto() == null ? "No indicó correo" : d.contacto(), "", "");
        p.seccion("Pago de la matrícula");
        p.par("Monto pagado", d.montoMatricula(), "Comprobante", d.comprobante() == null ? "—" : d.comprobante());
        p.par("Medio", d.medioPago(), "Fecha de pago", d.pagadoEn());
        p.seccion("Pensiones del año");
        if (d.pensiones().isEmpty()) {
            p.nota("El calendario de pensiones se mostrará en tu cuenta cuando esté generado.");
        } else {
            p.tabla3(d.pensiones(), "Cuota", "Vence", "Monto");
            p.par("Total pensiones", d.totalPensiones(), "", "");
        }
        p.seccion("Horario semanal");
        if (d.horario().isEmpty()) {
            p.nota("El horario de tu sección todavía no está publicado. Te avisaremos por el portal y por correo.");
        } else {
            p.horario(d.horario());
        }
        p.seccion("Cómo entrar al sistema");
        p.par("Usuario del alumno", d.usuarioAlumno() == null ? "(lo creará secretaría)" : d.usuarioAlumno(), "Usuario del apoderado",
            d.usuarioApoderado() == null ? "(ya tenías cuenta o la creará secretaría)" : d.usuarioApoderado());
        p.nota("La clave inicial de cada cuenta es el número de documento de su titular. Al entrar por primera vez el sistema pedirá cambiarla.");
        p.seccion("Pendiente en secretaría");
        p.nota("Presenta en secretaría antes del inicio de clases: copia del DNI del alumno, partida de nacimiento y ficha SIAGIE.");
        return p.cerrar();
    }

    // ------------------------------------------------------------------ bloques

    private void encabezado(Datos d) {
        rect(0, 842 - 96, 595, 96, AZUL);
        texto(40, 842 - 42, 20, true, BLANCO, d.colegio());
        texto(40, 842 - 68, 13, false, BLANCO, "CONSTANCIA DE MATRÍCULA " + d.anio());
        y = 842 - 126;
    }

    private void seccion(String titulo) {
        asegurar(70);
        y -= 6;
        rect(40, y - 4, 515, 20, SUAVE);
        texto(46, y + 1, 11, true, AZUL, titulo.toUpperCase(Locale.ROOT));
        y -= 24;
    }

    private void par(String e1, String v1, String e2, String v2) {
        asegurar(24);
        texto(46, y, 8.5, false, GRIS, e1);
        texto(46, y - 12, 10.5, true, NEGRO, recortar(v1, 38));
        if (e2 != null && !e2.isBlank()) {
            texto(310, y, 8.5, false, GRIS, e2);
            texto(310, y - 12, 10.5, true, NEGRO, recortar(v2, 38));
        }
        y -= 30;
    }

    private void nota(String texto) {
        for (String l : partir(texto, 95)) {
            asegurar(14);
            texto(46, y, 9.5, false, GRIS, l);
            y -= 13;
        }
        y -= 6;
    }

    private void tabla3(List<Fila> filas, String h1, String h2, String h3) {
        asegurar(20);
        texto(46, y, 9, true, AZUL, h1);
        texto(300, y, 9, true, AZUL, h2);
        texto(470, y, 9, true, AZUL, h3);
        y -= 4;
        linea(40, y, 555, y);
        y -= 13;
        for (Fila f : filas) {
            asegurar(16);
            texto(46, y, 9.5, false, NEGRO, recortar(f.a(), 50));
            texto(300, y, 9.5, false, NEGRO, f.b());
            texto(470, y, 9.5, false, NEGRO, f.c());
            y -= 14;
        }
        y -= 4;
    }

    private void horario(List<FilaHorario> filas) {
        asegurar(24 + 13.0 * filas.size());      // la tabla no se parte entre dos páginas
        texto(46, y, 8.5, true, AZUL, "Hora");
        for (int i = 0; i < 5; i++) texto(104 + i * 90, y, 8.5, true, AZUL, DIAS[i]);
        y -= 4;
        linea(40, y, 555, y);
        y -= 12;
        for (FilaHorario f : filas) {
            asegurar(16);
            texto(46, y, 8.5, true, NEGRO, f.hora());
            for (int i = 0; i < 5; i++) texto(104 + i * 90, y, 8, false, NEGRO, recortar(f.dias()[i] == null ? "" : f.dias()[i], 17));
            y -= 13;
        }
        y -= 6;
    }

    // ------------------------------------------------------------------ PDF de bajo nivel

    private void asegurar(double alto) {
        if (y - alto < 60) {
            nuevaPagina();
            y = 842 - 50;
        }
    }

    private void nuevaPagina() {
        pag = new StringBuilder();
        paginas.add(pag);
    }

    private void texto(double x, double yy, double tam, boolean negrita, double[] rgb, String t) {
        pag.append("BT /").append(negrita ? "F2" : "F1").append(' ').append(f(tam)).append(" Tf ")
            .append(f(rgb[0])).append(' ').append(f(rgb[1])).append(' ').append(f(rgb[2])).append(" rg ")
            .append(f(x)).append(' ').append(f(yy)).append(" Td (").append(escapar(t)).append(") Tj ET\n");
    }

    private void rect(double x, double yy, double w, double h, double[] rgb) {
        pag.append(f(rgb[0])).append(' ').append(f(rgb[1])).append(' ').append(f(rgb[2])).append(" rg ")
            .append(f(x)).append(' ').append(f(yy)).append(' ').append(f(w)).append(' ').append(f(h)).append(" re f\n");
    }

    private void linea(double x1, double y1, double x2, double y2) {
        pag.append("0.8 0.82 0.88 RG 0.6 w ").append(f(x1)).append(' ').append(f(y1)).append(" m ").append(f(x2)).append(' ')
            .append(f(y2)).append(" l S\n");
    }

    private byte[] cerrar() {
        int n = paginas.size();
        List<byte[]> objs = new ArrayList<>();
        objs.add(b("<< /Type /Catalog /Pages 2 0 R >>"));
        StringBuilder kids = new StringBuilder();
        for (int i = 0; i < n; i++) kids.append(5 + 2 * i).append(" 0 R ");
        objs.add(b("<< /Type /Pages /Kids [" + kids + "] /Count " + n + " >>"));
        objs.add(b("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>"));
        objs.add(b("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>"));
        for (int i = 0; i < n; i++) {
            objs.add(b("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 3 0 R /F2 4 0 R >> >> /Contents "
                + (6 + 2 * i) + " 0 R >>"));
            byte[] flujo = paginas.get(i).toString().getBytes(StandardCharsets.ISO_8859_1);
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            o.writeBytes(b("<< /Length " + flujo.length + " >>\nstream\n"));
            o.writeBytes(flujo);
            o.writeBytes(b("\nendstream"));
            objs.add(o.toByteArray());
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(b("%PDF-1.4\n"));
        int[] pos = new int[objs.size()];
        for (int i = 0; i < objs.size(); i++) {
            pos[i] = out.size();
            out.writeBytes(b((i + 1) + " 0 obj\n"));
            out.writeBytes(objs.get(i));
            out.writeBytes(b("\nendobj\n"));
        }
        int xref = out.size();
        StringBuilder x = new StringBuilder("xref\n0 " + (objs.size() + 1) + "\n0000000000 65535 f \n");
        for (int p : pos) x.append(String.format("%010d 00000 n \n", p));
        x.append("trailer\n<< /Size ").append(objs.size() + 1).append(" /Root 1 0 R >>\nstartxref\n").append(xref).append("\n%%EOF\n");
        out.writeBytes(b(x.toString()));
        return out.toByteArray();
    }

    // ------------------------------------------------------------------ utilidades

    private static String f(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    private static byte[] b(String s) {
        return s.getBytes(StandardCharsets.ISO_8859_1);
    }

    static String escapar(String t) {
        StringBuilder sb = new StringBuilder();
        for (char c : (t == null ? "" : t).toCharArray()) {
            if (c == '(' || c == ')' || c == '\\') sb.append('\\').append(c);
            else if (c == '\u2013') sb.append((char) 150);        // – (WinAnsi 0x96)
            else if (c == '\u2014') sb.append((char) 151);        // — (WinAnsi 0x97)
            else if (c > 255) sb.append('?');
            else if (c < 32) sb.append(' ');
            else sb.append(c);
        }
        return sb.toString();
    }

    static String recortar(String t, int max) {
        String s = t == null ? "" : t;
        return s.length() <= max ? s : s.substring(0, max - 1) + ".";
    }

    static List<String> partir(String texto, int max) {
        List<String> r = new ArrayList<>();
        StringBuilder l = new StringBuilder();
        for (String w : texto.split("\\s+")) {
            if (l.length() + w.length() + 1 > max) {
                r.add(l.toString());
                l.setLength(0);
            }
            if (l.length() > 0) l.append(' ');
            l.append(w);
        }
        if (l.length() > 0) r.add(l.toString());
        return r;
    }
}
