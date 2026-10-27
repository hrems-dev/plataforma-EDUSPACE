package pe.edu.upeu.eduspace.examenes.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Collections;
import java.util.List;

/** Contrato interno con alumnos-ms (adenda A3). */
@FeignClient(name = "alumnos-ms", fallback = AlumnosClient.AlumnosClientFallback.class)
public interface AlumnosClient {

    @GetMapping("/internal/alumnos/usuario/{usuarioId}")
    AlumnoUsuario alumnoDeUsuario(@PathVariable("usuarioId") Long usuarioId);

    @GetMapping("/internal/apoderados/usuario/{usuarioId}/alumnos")
    List<Hijo> hijosDeUsuario(@PathVariable("usuarioId") Long usuarioId);

    record AlumnoUsuario(Long alumnoId) {}

    record Hijo(Long alumnoId, String codigo, String nombreCompleto) {}

    @Component
    class AlumnosClientFallback implements AlumnosClient {
        @Override
        public AlumnoUsuario alumnoDeUsuario(Long usuarioId) {
            return null;
        }

        @Override
        public List<Hijo> hijosDeUsuario(Long usuarioId) {
            return Collections.emptyList();
        }
    }
}
