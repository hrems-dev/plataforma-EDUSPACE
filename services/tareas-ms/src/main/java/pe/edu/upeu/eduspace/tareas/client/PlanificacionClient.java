package pe.edu.upeu.eduspace.tareas.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

/** Contrato interno de planificacion-ms (adenda A2). */
@FeignClient(name = "planificacion-ms")
public interface PlanificacionClient {

    @GetMapping("/internal/docentes/usuario/{usuarioId}")
    Docente docentePorUsuario(@PathVariable("usuarioId") Long usuarioId);

    @GetMapping("/internal/asignaciones/validar")
    Boolean validarAsignacion(@RequestParam("docenteId") Long docenteId, @RequestParam("seccionId") Long seccionId,
                              @RequestParam("cursoId") Long cursoId);

    @GetMapping("/internal/secciones/{id}")
    Seccion seccion(@PathVariable("id") Long id);

    @GetMapping("/internal/periodos/{id}")
    Periodo periodo(@PathVariable("id") Long id);

    @GetMapping("/internal/cursos/{id}")
    Curso curso(@PathVariable("id") Long id);

    record Docente(Long docenteId, String nombre, String estado) {}

    record Seccion(Long id, String nombre, Long gradoId, Long nivelId, Long anioId, Short capacidad, String turno,
                   Long tutorDocenteId) {}

    record Periodo(Long id, Long anioId, Short numero, String nombre, LocalDate fechaInicio, LocalDate fechaFin, String estado) {}

    record Curso(Long id, String codigo, String nombre, String area, Boolean activo) {}
}
