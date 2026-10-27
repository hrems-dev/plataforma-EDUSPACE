package pe.edu.eduspace.notificaciones.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import pe.edu.eduspace.notificaciones.entity.Canal;

/** Aviso manual de un ADMIN a usuarios concretos, por la bandeja (IN_APP) y/o push. */
public record EnvioManualRequest(@NotEmpty List<Long> usuarioIds, @NotBlank @Size(max = 200) String titulo,
                                 @NotBlank String mensaje, @NotEmpty List<Canal> canales) {
}
