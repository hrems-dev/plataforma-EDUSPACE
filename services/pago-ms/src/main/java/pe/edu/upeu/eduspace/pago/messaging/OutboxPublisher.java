package pe.edu.upeu.eduspace.pago.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upeu.eduspace.pago.entity.OutboxEvento;
import pe.edu.upeu.eduspace.pago.repository.OutboxEventoRepository;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisher {

    private final OutboxEventoRepository outboxEventoRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelay = 2000)
    @Transactional
    public void publicarEventosPendientes() {
        List<OutboxEvento> pendientes = outboxEventoRepository.findPendientesForUpdate();
        if (pendientes.isEmpty()) {
            return;
        }

        for (OutboxEvento evento : pendientes) {
            try {
                kafkaTemplate.send(evento.getTopic(), evento.getClave(), evento.getPayload())
                    .whenComplete((result, ex) -> {
                        if (ex == null) {
                            evento.setEnviadoEn(LocalDateTime.now());
                            outboxEventoRepository.save(evento);
                            log.debug("Evento outbox {} publicado a {}", evento.getId(), evento.getTopic());
                        } else {
                            evento.setIntentos((short) (evento.getIntentos() + 1));
                            evento.setUltimoError(ex.getMessage() != null ? ex.getMessage().substring(0, Math.min(ex.getMessage().length(), 490)) : "Error desconocido");
                            outboxEventoRepository.save(evento);
                            log.error("Fallo al publicar evento outbox {}: {}", evento.getId(), ex.getMessage());
                        }
                    });
            } catch (Exception e) {
                evento.setIntentos((short) (evento.getIntentos() + 1));
                evento.setUltimoError(e.getMessage() != null ? e.getMessage().substring(0, Math.min(e.getMessage().length(), 490)) : "Error de envío");
                outboxEventoRepository.save(evento);
                log.error("Excepción inesperada al enviar evento outbox {}: {}", evento.getId(), e.getMessage());
            }
        }
    }
}

