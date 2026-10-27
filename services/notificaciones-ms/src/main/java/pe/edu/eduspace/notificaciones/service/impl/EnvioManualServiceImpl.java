package pe.edu.eduspace.notificaciones.service.impl;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.eduspace.notificaciones.dto.EnvioManualRequest;
import pe.edu.eduspace.notificaciones.dto.EnvioManualResponse;
import pe.edu.eduspace.notificaciones.entity.Canal;
import pe.edu.eduspace.notificaciones.entity.EstadoNotificacion;
import pe.edu.eduspace.notificaciones.entity.Notificacion;
import pe.edu.eduspace.notificaciones.exception.BusinessRuleException;
import pe.edu.eduspace.notificaciones.repository.NotificacionInsertador;
import pe.edu.eduspace.notificaciones.service.*;

/** Aviso manual de un ADMIN a usuarios concretos. El correo no está disponible: no se conocen direcciones de usuarios sueltos. */
@Service
@RequiredArgsConstructor
@Transactional
public class EnvioManualServiceImpl implements EnvioManualService {

    static final String TIPO = "AVISO_MANUAL";

    private final NotificacionInsertador insertador;
    private final PreferenciaService preferencias;
    private final DispositivoService dispositivos;
    private final ApplicationEventPublisher publisher;

    @Override
    public EnvioManualResponse enviar(EnvioManualRequest request) {
        if (request.canales().contains(Canal.EMAIL)) {
            throw new BusinessRuleException("El envío manual por correo no está disponible: use IN_APP y/o PUSH");
        }
        String clave = UUID.randomUUID().toString();
        int creadas = 0;
        int omitidas = 0;
        for (Long usuarioId : request.usuarioIds().stream().distinct().toList()) {
            for (Canal canal : request.canales().stream().distinct().toList()) {
                boolean sinDispositivo = canal == Canal.PUSH && dispositivos.tokensActivos(usuarioId).isEmpty();
                if (sinDispositivo || !preferencias.habilitada(usuarioId, TIPO, canal)) {
                    omitidas++;
                    continue;
                }
                Notificacion n = new Notificacion();
                n.setUsuarioId(usuarioId);
                n.setTipo(TIPO);
                n.setCanal(canal);
                n.setTitulo(request.titulo());
                n.setMensaje(request.mensaje());
                n.setEventoOrigen("manual");
                n.setEventoClave(clave);
                n.setEstado(canal == Canal.IN_APP ? EstadoNotificacion.ENVIADA : EstadoNotificacion.PENDIENTE);
                n.setEnviadaEn(canal == Canal.IN_APP ? LocalDateTime.now() : null);
                var id = insertador.insertar(n);
                if (id.isPresent()) {
                    creadas++;
                    if (canal != Canal.IN_APP) {
                        publisher.publishEvent(new NotificacionCreadaEvent(id.get()));
                    }
                } else {
                    omitidas++;
                }
            }
        }
        return new EnvioManualResponse(creadas, omitidas);
    }
}
