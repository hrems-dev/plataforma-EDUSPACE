package pe.edu.eduspace.notificaciones.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.eduspace.notificaciones.config.AppProperties;
import pe.edu.eduspace.notificaciones.entity.EstadoNotificacion;
import pe.edu.eduspace.notificaciones.entity.IntentoEnvio;
import pe.edu.eduspace.notificaciones.entity.Notificacion;
import pe.edu.eduspace.notificaciones.repository.IntentoEnvioRepository;
import pe.edu.eduspace.notificaciones.repository.NotificacionRepository;
import pe.edu.eduspace.notificaciones.service.*;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Entrega de EMAIL y PUSH, y resolución de las notificaciones PENDIENTES por falta de contactos. Cada intento deja
 * una fila en {@code intentos_envio}; al tercer fallo la notificación queda FALLIDA definitiva.
 */
@Service
public class EnvioNotificacionServiceImpl implements EnvioNotificacionService {

    private static final Logger log = LoggerFactory.getLogger(EnvioNotificacionServiceImpl.class);
    private static final TypeReference<Map<String, Object>> MAPA = new TypeReference<>() {
    };

    /** Se descarta sin reintentos: no hay a quién ni cómo entregarla. */
    private static final class Descartada extends RuntimeException {
        Descartada(String motivo) {
            super(motivo);
        }
    }

    private final NotificacionRepository repository;
    private final IntentoEnvioRepository intentoRepository;
    private final EmailSender emailSender;
    private final PushSender pushSender;
    private final DispositivoService dispositivos;
    private final NotificacionOrquestador orquestador;
    private final PendienteCodec codec;
    private final ObjectMapper objectMapper;
    private final AppProperties props;

    public EnvioNotificacionServiceImpl(NotificacionRepository repository, IntentoEnvioRepository intentoRepository,
                                        EmailSender emailSender, PushSender pushSender, DispositivoService dispositivos,
                                        NotificacionOrquestador orquestador, PendienteCodec codec,
                                        ObjectMapper objectMapper, AppProperties props) {
        this.repository = repository;
        this.intentoRepository = intentoRepository;
        this.emailSender = emailSender;
        this.pushSender = pushSender;
        this.dispositivos = dispositivos;
        this.orquestador = orquestador;
        this.codec = codec;
        this.objectMapper = objectMapper;
        this.props = props;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void intentar(Long notificacionId) {
        Notificacion n = repository.findById(notificacionId).orElse(null);
        if (n == null || (n.getEstado() != EstadoNotificacion.PENDIENTE && n.getEstado() != EstadoNotificacion.FALLIDA)) {
            return;
        }
        long previos = intentoRepository.countByNotificacionId(notificacionId);
        int max = props.reintentos().maxIntentos();
        if (previos >= max) {
            return;
        }
        short numero = (short) (previos + 1);
        boolean esPendienteDeContactos = Notificacion.SIN_DESTINATARIO == n.getUsuarioId();
        try {
            entregar(n);
            registrar(n, numero, true, null);
            n.setEstado(EstadoNotificacion.ENVIADA);
            n.setEnviadaEn(LocalDateTime.now());
        } catch (Descartada ex) {
            registrar(n, numero, false, ex.getMessage());
            n.setEstado(EstadoNotificacion.DESCARTADA);
            log.warn("component=EnvioNotificacion eventType={} key={} status=DESCARTADA cause={}", n.getTipo(), n.getId(), ex.getMessage());
        } catch (RuntimeException ex) {
            registrar(n, numero, false, ex.getMessage());
            // una pendiente de contactos sigue PENDIENTE mientras queden intentos; las demás pasan a FALLIDA
            n.setEstado(numero >= max || !esPendienteDeContactos ? EstadoNotificacion.FALLIDA : EstadoNotificacion.PENDIENTE);
            log.warn("component=EnvioNotificacion eventType={} key={} status={} intento={} cause={}", n.getTipo(), n.getId(),
                    n.getEstado(), numero, ex.getMessage());
        }
        repository.save(n);
    }

    private void entregar(Notificacion n) {
        if (Notificacion.SIN_DESTINATARIO == n.getUsuarioId()) {
            resolverPendiente(n);
            return;
        }
        switch (n.getCanal()) {
            case EMAIL -> {
                Object email = datos(n).get("email");
                if (email == null || email.toString().isBlank()) {
                    throw new Descartada("La notificación no tiene dirección de correo");
                }
                emailSender.enviar(email.toString(), n.getTitulo(), n.getMensaje());
            }
            case PUSH -> {
                List<String> tokens = dispositivos.tokensActivos(n.getUsuarioId());
                if (tokens.isEmpty()) {
                    throw new Descartada("El usuario no tiene dispositivos activos");
                }
                pushSender.enviar(tokens, n.getTitulo(), n.getMensaje());
            }
            case IN_APP -> {
                // la bandeja no necesita entrega: ya está disponible
            }
        }
    }

    private void resolverPendiente(Notificacion n) {
        PendienteCodec.Pendiente pendiente = codec.leer(n.getDatosJson());
        ResultadoResolucion resultado = orquestador.reintentarPendiente(pendiente.evento(), pendiente.alumnoId());
        switch (resultado) {
            case RESUELTO -> log.info("component=EnvioNotificacion eventType={} key={} status=RESUELTA alumnoId={}",
                    n.getTipo(), n.getId(), pendiente.alumnoId());
            case NO_EXISTE -> throw new Descartada("El alumno " + pendiente.alumnoId() + " no existe en alumnos-ms");
            case PENDIENTE -> throw new IllegalStateException("alumnos-ms sigue sin responder");
        }
    }

    private Map<String, Object> datos(Notificacion n) {
        try {
            return n.getDatosJson() == null ? Map.of() : objectMapper.readValue(n.getDatosJson(), MAPA);
        } catch (RuntimeException ex) {
            return Map.of();
        }
    }

    private void registrar(Notificacion n, short numero, boolean exitoso, String error) {
        IntentoEnvio intento = new IntentoEnvio();
        intento.setNotificacion(n);
        intento.setNumeroIntento(numero);
        intento.setExitoso(exitoso);
        intento.setError(error == null ? null : error.substring(0, Math.min(error.length(), 500)));
        intentoRepository.save(intento);
    }
}
