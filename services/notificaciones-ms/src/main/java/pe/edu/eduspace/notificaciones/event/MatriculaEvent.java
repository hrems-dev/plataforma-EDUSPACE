package pe.edu.eduspace.notificaciones.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;

/** Copia propia del contrato de {@code matricula.creada / .confirmada / .anulada}: solo los campos que este servicio usa. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MatriculaEvent(UUID eventId, Long alumnoId, String alumnoNombre, Long matriculaId, String anio, Long anioId,
                             String gradoNombre, String seccionNombre, String motivo) {
}
