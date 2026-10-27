package pe.edu.eduspace.notificaciones.dto;

import java.time.LocalDateTime;
import java.util.Map;

public record NotificacionResponse(Long id, String tipo, String titulo, String mensaje, Map<String, Object> datos,
                                   Boolean leida, LocalDateTime leidaEn, LocalDateTime creadaEn) {
}
