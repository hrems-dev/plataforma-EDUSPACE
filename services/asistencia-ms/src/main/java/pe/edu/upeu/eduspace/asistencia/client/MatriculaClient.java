package pe.edu.upeu.eduspace.asistencia.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

/** Carga inicial de la réplica cuando una sección aún no tiene alumnos locales. */
@FeignClient(name = "matricula-ms")
public interface MatriculaClient {

    @GetMapping("/internal/matriculas/seccion/{seccionId}")
    List<MatriculaInterna> deSeccion(@PathVariable("seccionId") Long seccionId);

    record MatriculaInterna(Long matriculaId, Long alumnoId, String alumnoNombre, Long anioId, Long nivelId,
                            Long gradoId, Long seccionId, String estado, String tipo) {}
}
