package pe.edu.upeu.eduspace.matricula.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.math.BigDecimal;

/** Consulta de deuda para la ratificación (pago-ms). */
@FeignClient(name = "pago-ms")
public interface PagoClient {

    @GetMapping("/internal/pagos/alumno/{id}/deuda")
    Deuda deuda(@PathVariable("id") Long alumnoId);

    record Deuda(Long alumnoId, boolean tieneDeudaVencida, BigDecimal montoVencido, Integer cuotasVencidasCount) {}
}
