package pe.edu.eduspace.notificaciones.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Copia local del contrato {@code GET /internal/alumnos/{id}/contactos} de alumnos-ms. Es propiedad de este
 * servicio: no se comparte código entre servicios, y los campos que no usamos se ignoran.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ContactosDto(Long alumnoId, String nombreCompleto, Long usuarioId, List<ApoderadoDto> apoderados) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApoderadoDto(Long apoderadoId, Long usuarioId, String nombreCompleto, String email, String telefono,
                               Boolean esPrincipal, Boolean esResponsablePago) {
    }

    public List<ApoderadoDto> apoderadosOVacio() {
        return apoderados == null ? List.of() : apoderados;
    }
}
