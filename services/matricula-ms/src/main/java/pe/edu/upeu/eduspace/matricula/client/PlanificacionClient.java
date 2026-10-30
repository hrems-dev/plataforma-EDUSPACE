package pe.edu.upeu.eduspace.matricula.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;

/** Contrato interno de planificacion-ms (adenda A2). */
@FeignClient(name = "planificacion-ms")
public interface PlanificacionClient {

    @GetMapping("/internal/secciones/{id}")
    Seccion seccion(@PathVariable("id") Long id);

    @GetMapping("/internal/secciones")
    List<Seccion> secciones(@RequestParam(value = "anioId", required = false) Long anioId,
                            @RequestParam(value = "gradoId", required = false) Long gradoId);

    @GetMapping("/internal/anios/activo")
    Anio anioActivo();

    @GetMapping("/internal/anios/{id}")
    Anio anio(@PathVariable("id") Long id);

    @GetMapping("/internal/grados/{id}")
    Grado grado(@PathVariable("id") Long id);

    @GetMapping("/internal/horarios/seccion/{id}")
    HorarioSeccion horario(@PathVariable("id") Long id);

    record Seccion(Long id, String nombre, Long gradoId, Long nivelId, Long anioId, Short capacidad, String turno,
                   Long tutorDocenteId) {}

    record Anio(Long id, String nombre, LocalDate fechaInicio, LocalDate fechaFin, String estado) {}

    record Grado(Long id, Long nivelId, String nivelNombre, Short numero, String nombre, Boolean activo) {}

    record HorarioSeccion(Long seccionId, String seccionNombre, String turno, List<Clase> clases) {}

    record Clase(String cursoNombre, String docenteNombre, Short diaSemana, Short bloqueOrden, String horaInicio, String horaFin,
                 String aulaNombre) {}
}
