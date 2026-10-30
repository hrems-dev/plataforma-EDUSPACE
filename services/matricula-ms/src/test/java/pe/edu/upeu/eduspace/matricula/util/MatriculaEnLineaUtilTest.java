package pe.edu.upeu.eduspace.matricula.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

class MatriculaEnLineaUtilTest {

    private static final YearMonth HOY = YearMonth.of(2026, 10);

    @Test
    void laTarjetaDePruebaSeApruebaYNoSeGuardaElNumeroCompleto() {
        var r = TarjetaSimulada.procesar("4242 4242 4242 4242", "12/28", "123", HOY);
        assertThat(r.aprobada()).isTrue();
        assertThat(r.referencia()).isEqualTo("VISA ****4242").doesNotContain("4242 4242");
    }

    @Test
    void laTarjetaQueTerminaEn0002SeRechazaYLasInvalidasNoPasan() {
        assertThat(TarjetaSimulada.procesar("4000000000000002", "12/28", "123", HOY).aprobada()).isFalse();
        assertThat(TarjetaSimulada.procesar("4242424242424241", "12/28", "123", HOY).mensaje()).contains("número");   // Luhn
        assertThat(TarjetaSimulada.procesar("4242424242424242", "09/26", "123", HOY).mensaje()).contains("vencida");
        assertThat(TarjetaSimulada.procesar("4242424242424242", "1228", "123", HOY).mensaje()).contains("MM/AA");
        assertThat(TarjetaSimulada.procesar("4242424242424242", "12/28", "12", HOY).mensaje()).contains("CVV");
    }

    @Test
    void laConstanciaEsUnPdfConLosDatosYPaginaSiHaceFalta() {
        List<PdfConstancia.Fila> pensiones = new java.util.ArrayList<>();
        for (int i = 1; i <= 10; i++) pensiones.add(new PdfConstancia.Fila("Pensión " + i + "/2026", "05/0" + (i % 9 + 1) + "/2026", "S/ 30.00"));
        List<PdfConstancia.FilaHorario> horario = List.of(new PdfConstancia.FilaHorario("08:00–08:45", new String[] {"Matemática", "Comunicación", null, "Inglés", "Arte"}));
        byte[] pdf = PdfConstancia.generar(new PdfConstancia.Datos("Colegio (Prueba)", "2026", "MAT-2026-000001", "29/10/2026", "Ríos Pérez, Ana", "70000000", "1° de Primaria",
            "A", "mañana", "Carmen Ríos", "40123456", "carmen@ejemplo.pe", "S/ 100.00", "R001-00000009", "Pago en línea (simulado)", "29/10/2026 10:00",
            pensiones, "S/ 300.00", horario, "ana.rios", null));
        String texto = new String(pdf, StandardCharsets.ISO_8859_1);
        assertThat(texto).startsWith("%PDF-1.4").endsWith("%%EOF\n");
        assertThat(texto).contains("CONSTANCIA DE MATR").contains("MAT-2026-000001").contains("ana.rios").contains("Matem");
        assertThat(PdfConstancia.escapar("a(b)c\\d")).isEqualTo("a\\(b\\)c\\\\d");
        assertThat(PdfConstancia.partir("uno dos tres cuatro", 9)).containsExactly("uno dos", "tres", "cuatro");
    }
}
