package pe.edu.eduspace.notificaciones.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Cliente de alumnos-ms (resuelto por Eureka). Si {@code app.clients.alumnos.url} tiene valor, se llama directo a esa
 * URL (pruebas y desarrollo sin Eureka).
 */
@FeignClient(name = "alumnos-ms", url = "${app.clients.alumnos.url:}")
public interface AlumnosClient {

    @GetMapping("/internal/alumnos/{id}/contactos")
    ContactosDto contactos(@PathVariable("id") Long alumnoId);
}
