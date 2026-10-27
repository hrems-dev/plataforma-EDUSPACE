package pe.edu.eduspace.notificaciones.service.impl;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import pe.edu.eduspace.notificaciones.service.Destino;
import pe.edu.eduspace.notificaciones.service.EventoNotificable;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** Guarda y recupera, dentro de una notificación PENDIENTE, lo necesario para volver a resolver sus destinatarios. */
@Component
class PendienteCodec {

    private static final TypeReference<Map<String, Object>> MAPA = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, String>> MAPA_TEXTO = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    PendienteCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    String escribir(EventoNotificable e, Long alumnoId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("alumnoId", alumnoId);
        payload.put("destino", e.destino().name());
        payload.put("plantilla", e.plantilla());
        payload.put("eventType", e.eventType());
        payload.put("claveEvento", e.claveEvento());
        payload.put("datos", e.datos());
        return objectMapper.writeValueAsString(payload);
    }

    Pendiente leer(String json) {
        Map<String, Object> payload = objectMapper.readValue(json, MAPA);
        Map<String, String> datos = objectMapper.convertValue(payload.get("datos"), MAPA_TEXTO);
        Long alumnoId = ((Number) payload.get("alumnoId")).longValue();
        EventoNotificable evento = new EventoNotificable(null, (String) payload.get("eventType"),
                (String) payload.get("claveEvento"), (String) payload.get("plantilla"),
                Destino.valueOf((String) payload.get("destino")), List.of(alumnoId), List.of(), datos);
        return new Pendiente(evento, alumnoId);
    }

    record Pendiente(EventoNotificable evento, Long alumnoId) {
    }

    String escribirDatos(Map<String, ?> datos) {
        return objectMapper.writeValueAsString(datos);
    }
}
