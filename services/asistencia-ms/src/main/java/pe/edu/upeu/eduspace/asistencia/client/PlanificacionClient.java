package pe.edu.upeu.eduspace.asistencia.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;

/** Contrato interno de planificacion-ms (adenda A2). */
@FeignClient(name = "planificacion-ms")
public interface PlanificacionClient {

    @GetMapping("/internal/docentes/usuario/{usuarioId}")
    Docente docentePorUsuario(@PathVariable("usuarioId") Long usuarioId);

    @GetMapping("/internal/docentes/usuario/{usuarioId}/asignaciones")
    List<Asignacion> asignaciones(@PathVariable("usuarioId") Long usuarioId, @RequestParam(value = "anioId", required = false) Long anioId);

    @GetMapping("/internal/asignaciones/validar")
    Boolean validarAsignacion(@RequestParam("docenteId") Long docenteId, @RequestParam("seccionId") Long seccionId,
                              @RequestParam("cursoId") Long cursoId);

    @GetMapping("/internal/feriados/es-habil")
    Boolean esHabil(@RequestParam("fecha") @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate fecha);

    @GetMapping("/internal/secciones/{id}")
    Seccion seccion(@PathVariable("id") Long id);

    @GetMapping("/internal/periodos")
    List<Periodo> periodos(@RequestParam("anioId") Long anioId);

    @GetMapping("/internal/periodos/{id}")
    Periodo periodo(@PathVariable("id") Long id);

    record Docente(Long docenteId, String nombre, String estado) {}

    record Asignacion(Long seccionId, Long cursoId, Long asignacionId) {}

    record Seccion(Long id, String nombre, Long gradoId, Long nivelId, Long anioId, Short capacidad, String turno,
                   Long tutorDocenteId) {}

    record Periodo(Long id, Long anioId, Short numero, String nombre, LocalDate fechaInicio, LocalDate fechaFin, String estado) {}
}
