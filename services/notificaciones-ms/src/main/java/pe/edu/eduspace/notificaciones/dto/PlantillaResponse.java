package pe.edu.eduspace.notificaciones.dto;

import java.time.LocalDateTime;
import pe.edu.eduspace.notificaciones.entity.Canal;

public record PlantillaResponse(Long id, String codigo, Canal canal, String asunto, String cuerpo, Boolean activa,
                                LocalDateTime actualizadoEn) {
}
