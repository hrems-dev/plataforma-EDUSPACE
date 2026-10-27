package pe.edu.eduspace.notificaciones.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Un evento de otro servicio ya traducido a lo que este servicio necesita para notificar: qué plantilla, a quién,
 * con qué datos y bajo qué clave se evita duplicarlo.
 *
 * @param eventId       {@code eventId} del sobre estándar; nulo si el productor no lo envía
 * @param eventType     tópico de origen (queda en {@code evento_origen})
 * @param claveEvento   identificador de la entidad del evento (queda en {@code evento_clave})
 * @param plantilla     código de plantilla, que también es el tipo de la notificación
 * @param alumnoIds     alumnos cuyos contactos hay que resolver (vacío con {@link Destino#USUARIOS_DIRECTOS})
 * @param usuarioIds    destinatarios directos (solo con {@link Destino#USUARIOS_DIRECTOS})
 * @param datos         valores para los marcadores {{campo}} de la plantilla
 */
public record EventoNotificable(UUID eventId, String eventType, String claveEvento, String plantilla, Destino destino,
                                List<Long> alumnoIds, List<Long> usuarioIds, Map<String, String> datos) {

    public EventoNotificable {
        alumnoIds = alumnoIds == null ? List.of() : alumnoIds;
        usuarioIds = usuarioIds == null ? List.of() : usuarioIds;
        datos = datos == null ? Map.of() : datos;
    }
}
