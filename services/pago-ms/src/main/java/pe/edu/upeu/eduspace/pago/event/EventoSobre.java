package pe.edu.upeu.eduspace.pago.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Sobre estándar de eventos (plan §5.1): los campos de negocio van PLANOS junto a
 * eventId, eventType, occurredAt, version y traceId. {@link #escribir} y {@link #leer}
 * convierten entre este objeto y el JSON plano; leer también tolera el formato anidado antiguo.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventoSobre<T> {

    private UUID eventId;
    private String eventType;
    private LocalDateTime occurredAt;
    @Builder.Default
    private Integer version = 1;
    private String traceId;
    private T payload;

    public static <T> String escribir(ObjectMapper mapper, EventoSobre<T> sobre) {
        ObjectNode nodo = sobre.getPayload() == null ? mapper.createObjectNode() : mapper.valueToTree(sobre.getPayload());
        nodo.put("eventId", sobre.getEventId().toString());
        nodo.put("eventType", sobre.getEventType());
        nodo.put("occurredAt", sobre.getOccurredAt().toString());
        nodo.put("version", sobre.getVersion() == null ? 1 : sobre.getVersion());
        nodo.put("traceId", sobre.getTraceId());
        return mapper.writeValueAsString(nodo);
    }

    public static <T> EventoSobre<T> leer(ObjectMapper mapper, String json, Class<T> tipo) {
        JsonNode raiz = mapper.readTree(json);
        JsonNode datos = raiz.has("payload") && raiz.get("payload").isObject() ? raiz.get("payload") : raiz;
        return EventoSobre.<T>builder()
                .eventId(texto(raiz, "eventId") == null ? null : UUID.fromString(texto(raiz, "eventId")))
                .eventType(texto(raiz, "eventType"))
                .occurredAt(texto(raiz, "occurredAt") == null ? null : LocalDateTime.parse(texto(raiz, "occurredAt").replaceAll("(Z|[+-]\\d\\d:\\d\\d)$", "")))
                .version(raiz.has("version") ? raiz.get("version").asInt(1) : 1)
                .traceId(texto(raiz, "traceId"))
                .payload(mapper.treeToValue(datos, tipo))
                .build();
    }

    private static String texto(JsonNode n, String campo) {
        JsonNode v = n.get(campo);
        return v == null || v.isNull() ? null : v.asText();
    }
}
