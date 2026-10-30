package pe.edu.upeu.eduspace.matricula.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/** Consulta de deuda para la ratificación (pago-ms). */
@FeignClient(name = "pago-ms")
public interface PagoClient {

    @GetMapping("/internal/pagos/alumno/{id}/deuda")
    Deuda deuda(@PathVariable("id") Long alumnoId);

    @GetMapping("/internal/tarifas/aplicable")
    OfertaCosto tarifa(@RequestParam("anioId") Long anioId, @RequestParam("nivelId") Long nivelId, @RequestParam("gradoId") Long gradoId);

    @GetMapping("/internal/pagos/matricula/{id}/cuotas")
    java.util.List<CuotaResumen> cuotas(@PathVariable("id") Long matriculaId);

    @PostMapping("/internal/pagos/matricula-en-linea")
    ResultadoCobro cobrar(@RequestBody CobroMatricula body);

    record Costo(Long tarifaId, BigDecimal monto, Short numCuotas, Short mesInicio, Short diaVencimiento) {}

    record OfertaCosto(Costo matricula, Costo pension) {}

    record CuotaResumen(Long id, String concepto, String descripcion, Short numeroCuota, BigDecimal montoBase, BigDecimal descuento,
                        BigDecimal montoTotal, BigDecimal saldo, String fechaVencimiento, String estado) {}

    record CobroMatricula(Long matriculaId, Long alumnoId, String pagadorNombre, String pagadorDocumento, String referencia) {}

    record ResultadoCobro(Long pagoId, String comprobante, BigDecimal monto, Long cuotaId) {}

    record Deuda(Long alumnoId, boolean tieneDeudaVencida, BigDecimal montoVencido, Integer cuotasVencidasCount) {}
}
