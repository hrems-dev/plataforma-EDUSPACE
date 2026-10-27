package pe.edu.eduspace.notificaciones.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import pe.edu.eduspace.notificaciones.entity.PlantillaNotificacion;
import pe.edu.eduspace.notificaciones.service.impl.PlantillaServiceImpl;
import pe.edu.eduspace.notificaciones.util.Datos;

class PlantillaYDatosTest {

    private final PlantillaServiceImpl service = new PlantillaServiceImpl(null, null);

    private PlantillaNotificacion plantilla(String asunto, String cuerpo) {
        PlantillaNotificacion p = new PlantillaNotificacion();
        p.setAsunto(asunto);
        p.setCuerpo(cuerpo);
        return p;
    }

    @Test
    void reemplazaLosMarcadoresConONSinEspacios() {
        var texto = service.armar(plantilla("Pago de {{ alumno }}", "S/ {{monto}} por {{concepto}}"),
                Map.of("alumno", "Luis", "monto", "150.00", "concepto", "Matrícula"));
        assertThat(texto.asunto()).isEqualTo("Pago de Luis");
        assertThat(texto.cuerpo()).isEqualTo("S/ 150.00 por Matrícula");
    }

    @Test
    void unDatoFaltanteDejaUnTextoNeutroEnLugarDeFallar() {
        var texto = service.armar(plantilla(null, "Nota de {{curso}}: {{nota}}"), Map.of("curso", "Matemática"));
        assertThat(texto.asunto()).isEmpty();
        assertThat(texto.cuerpo()).isEqualTo("Nota de Matemática: (sin dato)");
    }

    @Test
    void losValoresConSimbolosEspecialesNoRompenElReemplazo() {
        var texto = service.armar(plantilla("x", "Mensaje: {{motivo}}"), Map.of("motivo", "Pago de $100 \\ 50%"));
        assertThat(texto.cuerpo()).isEqualTo("Mensaje: Pago de $100 \\ 50%");
    }

    @Test
    void datosOmiteLosNulosYLosVacios() {
        assertThat(Datos.de("a", "1", "b", null, "c", " ", "d", 4)).containsOnlyKeys("a", "d").containsEntry("d", "4");
    }

    @Test
    void formatosDeDineroNotaYFecha() {
        assertThat(Datos.dinero(new BigDecimal("150"))).isEqualTo("150.00");
        assertThat(Datos.dinero(new BigDecimal("300.555"))).isEqualTo("300.56");
        assertThat(Datos.dinero(null)).isNull();
        assertThat(Datos.nota(new BigDecimal("17.50"))).isEqualTo("17.5");
        assertThat(Datos.nota(new BigDecimal("18.00"))).isEqualTo("18");
        assertThat(Datos.fecha("2026-03-31")).isEqualTo("31/03/2026");
        assertThat(Datos.fecha("2026-06-10T23:59:00")).isEqualTo("10/06/2026");
        assertThat(Datos.fecha(List.of(2026, 4, 30))).isEqualTo("30/04/2026");
        assertThat(Datos.fecha("pronto")).isEqualTo("pronto");
        assertThat(Datos.fecha(null)).isNull();
    }
}
