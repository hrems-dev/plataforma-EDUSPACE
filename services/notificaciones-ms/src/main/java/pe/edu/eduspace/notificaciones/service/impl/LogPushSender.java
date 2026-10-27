package pe.edu.eduspace.notificaciones.service.impl;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pe.edu.eduspace.notificaciones.service.PushSender;

/** MVP: solo deja constancia del envío en el log (sin proveedor push real). */
@Component
public class LogPushSender implements PushSender {

    private static final Logger log = LoggerFactory.getLogger(LogPushSender.class);

    @Override
    public void enviar(List<String> tokens, String titulo, String mensaje) {
        log.info("component=LogPushSender eventType=PUSH status=SIMULADO dispositivos={} titulo={}", tokens.size(), titulo);
    }
}
