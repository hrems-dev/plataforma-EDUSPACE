package pe.edu.upeu.eduspace.calificaciones.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@FeignClient(name = "planificacion-ms", fallback = PlanificacionClient.PlanificacionClientFallback.class)
public interface PlanificacionClient {

    @GetMapping("/internal/docentes/usuario/{usuarioId}/asignaciones")
    List<AsignacionDocenteResponse> getAsignacionesDocente(@PathVariable("usuarioId") Long usuarioId,
                                                           @RequestParam(value = "anioId", required = false) Long anioId);

    @GetMapping("/internal/asignaciones/validar")
    Boolean validarAsignacionDocente(@RequestParam("docenteId") Long docenteId,
                                     @RequestParam("seccionId") Long seccionId,
                                     @RequestParam("cursoId") Long cursoId);

    @GetMapping("/internal/docentes/usuario/{usuarioId}")
    DocenteInterno getDocentePorUsuario(@PathVariable("usuarioId") Long usuarioId);

    record DocenteInterno(Long docenteId, String nombre, String estado) {}

    @GetMapping("/internal/cursos/{id}")
    Map<String, Object> getCursoPorId(@PathVariable("id") Long cursoId);

    @GetMapping("/internal/periodos/{id}")
    Map<String, Object> getPeriodoPorId(@PathVariable("id") Long periodoId);

    @GetMapping("/internal/anios/{id}")
    Map<String, Object> getAnioActivo(@PathVariable("id") Long anioId);

    @GetMapping("/internal/niveles/{id}")
    Map<String, Object> getNivelPorId(@PathVariable("id") Long nivelId);

    @Component
    class PlanificacionClientFallback implements PlanificacionClient {

        @Override
        public List<AsignacionDocenteResponse> getAsignacionesDocente(Long usuarioId, Long anioId) {
            return Collections.emptyList();
        }

        @Override
        public Boolean validarAsignacionDocente(Long docenteId, Long seccionId, Long cursoId) {
            // Regla de seguridad (H2): si cae el servicio, denegar acceso de escritura
            return false;
        }

        @Override
        public DocenteInterno getDocentePorUsuario(Long usuarioId) {
            return null;
        }

        @Override
        public Map<String, Object> getCursoPorId(Long cursoId) {
            return Map.of("id", cursoId, "nombre", "Curso " + cursoId);
        }

        @Override
        public Map<String, Object> getPeriodoPorId(Long periodoId) {
            return Map.of("id", periodoId, "nombre", "Periodo " + periodoId);
        }

        @Override
        public Map<String, Object> getAnioActivo(Long anioId) {
            return Map.of("id", anioId, "nombre", "Año " + anioId);
        }

        @Override
        public Map<String, Object> getNivelPorId(Long nivelId) {
            return Map.of();
        }
    }
}
