package pe.edu.eduspace.notificaciones.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import pe.edu.eduspace.notificaciones.service.EnvioNotificacionService;
import pe.edu.eduspace.notificaciones.service.NotificacionCreadaEvent;

/**
 * Entrega los correos y pushes recién creados, después del commit y fuera de la transacción del consumidor (el SMTP
 * puede tardar). Si falla, el planificador de reintentos la retoma.
 */
@Component
public class EnvioInmediatoListener {

    private static final Logger log = LoggerFactory.getLogger(EnvioInmediatoListener.class);

    private final EnvioNotificacionService envio;

    public EnvioInmediatoListener(EnvioNotificacionService envio) {
        this.envio = envio;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void alCrear(NotificacionCreadaEvent evento) {
        try {
            envio.intentar(evento.notificacionId());
        } catch (RuntimeException ex) {
            log.error("component=EnvioInmediatoListener eventType=Envio key={} status=ERROR cause={}", evento.notificacionId(), ex.getMessage());
        }
    }
}
