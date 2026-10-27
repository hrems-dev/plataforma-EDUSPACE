package pe.edu.upeu.eduspace.examenes.client;

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

    @GetMapping("/internal/cursos/{id}")
    Map<String, Object> getCursoPorId(@PathVariable("id") Long cursoId);

    @GetMapping("/internal/docentes/usuario/{usuarioId}")
    DocenteInterno getDocentePorUsuario(@PathVariable("usuarioId") Long usuarioId);

    record DocenteInterno(Long docenteId, String nombre, String estado) {}

    @Component
    class PlanificacionClientFallback implements PlanificacionClient {

        @Override
        public List<AsignacionDocenteResponse> getAsignacionesDocente(Long usuarioId, Long anioId) {
            return Collections.emptyList();
        }

        @Override
        public Boolean validarAsignacionDocente(Long docenteId, Long seccionId, Long cursoId) {
            // Seguridad primero: si cae el servicio, denegar escritura (H2)
            return false;
        }

        @Override
        public Map<String, Object> getCursoPorId(Long cursoId) {
            return Map.of("id", cursoId, "nombre", "Curso " + cursoId);
        }

        @Override
        public DocenteInterno getDocentePorUsuario(Long usuarioId) {
            return null;
        }
    }
}
