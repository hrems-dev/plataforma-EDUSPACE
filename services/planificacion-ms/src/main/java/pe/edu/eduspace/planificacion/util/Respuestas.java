package pe.edu.eduspace.planificacion.util;

import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

public final class Respuestas {

    private Respuestas() {
    }

    /** 201 con cabecera {@code Location} apuntando a {@code <ruta actual>/{id}}. */
    public static <T> ResponseEntity<T> creado(Long id, T cuerpo) {
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(id).toUri()).body(cuerpo);
    }
}
