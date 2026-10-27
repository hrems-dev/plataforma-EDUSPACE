package pe.edu.upeu.eduspace.matricula.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upeu.eduspace.matricula.entity.OutboxEvento;
import pe.edu.upeu.eduspace.matricula.event.EventoSobre;
import pe.edu.upeu.eduspace.matricula.event.MatriculaEvent;
import pe.edu.upeu.eduspace.matricula.repository.OutboxEventoRepository;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.UUID;

/** Escribe eventos en el outbox dentro de la MISMA transacción del cambio de negocio. */
@Service
@RequiredArgsConstructor
public class EventoOutboxService {

    private final OutboxEventoRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public void registrar(String topic, MatriculaEvent payload) {
        UUID eventId = UUID.randomUUID();
        String traceId = MDC.get("traceId");
        EventoSobre<MatriculaEvent> sobre = EventoSobre.<MatriculaEvent>builder()
            .eventId(eventId)
            .eventType(topic)
            .occurredAt(LocalDateTime.now())
            .version(1)
            .traceId(traceId != null ? traceId : UUID.randomUUID().toString())
            .payload(payload)
            .build();
        outboxRepository.save(OutboxEvento.builder()
            .id(eventId)
            .aggregateType("MATRICULA")
            .aggregateId(String.valueOf(payload.getMatriculaId()))
            .topic(topic)
            .clave(String.valueOf(payload.getMatriculaId()))
            .payload(EventoSobre.escribir(objectMapper, sobre))
            .build());
    }
}
