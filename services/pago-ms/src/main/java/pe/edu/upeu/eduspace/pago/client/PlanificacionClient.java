package pe.edu.upeu.eduspace.pago.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.time.LocalDate;

@FeignClient(name = "planificacion-ms", fallback = PlanificacionClient.PlanificacionClientFallback.class)
public interface PlanificacionClient {

    /** Año académico (id, nombre, fechaInicio, fechaFin, estado). */
    @GetMapping("/internal/anios/{id}")
    AnioInterno getAnio(@PathVariable("id") Long anioId);

    record AnioInterno(Long id, String nombre, LocalDate fechaInicio, LocalDate fechaFin, String estado) {}

    @Component
    class PlanificacionClientFallback implements PlanificacionClient {
        @Override
        public AnioInterno getAnio(Long anioId) {
            return null;
        }
    }
}
