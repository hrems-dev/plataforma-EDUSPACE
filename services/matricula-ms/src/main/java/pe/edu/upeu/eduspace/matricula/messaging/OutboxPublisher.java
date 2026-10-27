package pe.edu.upeu.eduspace.matricula.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upeu.eduspace.matricula.config.MatriculaProperties;
import pe.edu.upeu.eduspace.matricula.entity.OutboxEvento;
import pe.edu.upeu.eduspace.matricula.repository.OutboxEventoRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Envía a Kafka los eventos pendientes del outbox. Toma un lote con FOR UPDATE SKIP LOCKED (varias réplicas
 * no se pisan), espera la confirmación del broker y marca enviado; si falla suma un intento y guarda el error.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisher {

    private final OutboxEventoRepository repo;
    private final KafkaTemplate<String, String> kafka;
    private final MatriculaProperties props;

    @Scheduled(fixedDelay = 1500)
    @Transactional
    public void publicarPendientes() {
        List<OutboxEvento> lote = repo.tomarLotePendiente(props.getMaxIntentosOutbox());
        for (OutboxEvento e : lote) {
            try {
                kafka.send(e.getTopic(), e.getClave(), e.getPayload()).get(5, TimeUnit.SECONDS);
                e.setEnviadoEn(LocalDateTime.now());
                e.setUltimoError(null);
            } catch (Exception ex) {
                if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
                e.setIntentos((short) (e.getIntentos() + 1));
                e.setUltimoError(recortar(ex.getMessage()));
                if (e.getIntentos() >= props.getMaxIntentosOutbox()) {
                    log.error("ALERTA: evento outbox {} ({}) superó {} intentos: {}", e.getId(), e.getTopic(),
                        props.getMaxIntentosOutbox(), e.getUltimoError());
                } else {
                    log.warn("Fallo al publicar outbox {} ({}), intento {}: {}", e.getId(), e.getTopic(), e.getIntentos(), e.getUltimoError());
                }
                break; // conserva el orden: no se adelantan eventos posteriores
            }
        }
    }

    private static String recortar(String s) {
        if (s == null) return "Error desconocido";
        return s.length() > 480 ? s.substring(0, 480) : s;
    }
}
