package pe.edu.eduspace.notificaciones.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.UUID;

/** {@code versionHorario} es la versión del horario; {@code version} del sobre es la del contrato y se ignora. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record HorarioPublicadoEvent(UUID eventId, Long seccionId, String seccionNombre, Integer versionHorario,
                                    List<Long> docenteUsuarioIds) {
}
