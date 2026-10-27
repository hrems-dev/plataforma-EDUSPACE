package pe.edu.upeu.eduspace.matricula.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

/** Contrato interno de alumnos-ms (adenda A3). */
@FeignClient(name = "alumnos-ms")
public interface AlumnosClient {

    @GetMapping("/internal/alumnos/{id}")
    AlumnoBasico alumno(@PathVariable("id") Long id);

    @GetMapping("/internal/alumnos/{id}/expediente-completo")
    Expediente expediente(@PathVariable("id") Long id);

    @GetMapping("/internal/alumnos/{id}/responsable-pago")
    ResponsablePago responsablePago(@PathVariable("id") Long id);

    @GetMapping("/internal/alumnos/usuario/{usuarioId}")
    AlumnoUsuario alumnoDeUsuario(@PathVariable("usuarioId") Long usuarioId);

    @GetMapping("/internal/apoderados/usuario/{usuarioId}/alumnos")
    List<Hijo> hijosDeUsuario(@PathVariable("usuarioId") Long usuarioId);

    record AlumnoBasico(Long id, String codigo, String nombreCompleto, String estado) {}

    record Expediente(boolean completo, List<String> faltantes) {}

    record ResponsablePago(Long apoderadoId, Long usuarioId, String nombre) {}

    record AlumnoUsuario(Long alumnoId) {}

    record Hijo(Long alumnoId, String codigo, String nombreCompleto, String estado, String parentesco) {}
}
