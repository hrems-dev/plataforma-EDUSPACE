package pe.edu.upeu.eduspace.pago.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Collections;
import java.util.List;

@FeignClient(name = "alumnos-ms", fallback = AlumnosClient.AlumnosClientFallback.class)
public interface AlumnosClient {

    @GetMapping("/internal/alumnos/{id}/responsable-pago")
    AlumnoContactoResponse getResponsablePago(@PathVariable("id") Long alumnoId);

    @GetMapping("/internal/apoderados/usuario/{usuarioId}/alumnos")
    List<AlumnoContactoResponse.HijoResumen> getHijosPorUsuario(@PathVariable("usuarioId") Long usuarioId);

    @Component
    class AlumnosClientFallback implements AlumnosClient {

        @Override
        public AlumnoContactoResponse getResponsablePago(Long alumnoId) {
            // Si el servicio no responde, se crea la cuota sin responsable y se completa luego
            return null;
        }

        @Override
        public List<AlumnoContactoResponse.HijoResumen> getHijosPorUsuario(Long usuarioId) {
            return Collections.emptyList();
        }
    }
}

