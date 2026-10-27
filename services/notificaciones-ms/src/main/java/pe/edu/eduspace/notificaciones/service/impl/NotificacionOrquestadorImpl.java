package pe.edu.eduspace.notificaciones.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.eduspace.notificaciones.client.ContactosDto;
import pe.edu.eduspace.notificaciones.client.ContactosDto.ApoderadoDto;
import pe.edu.eduspace.notificaciones.client.ContactosResultado;
import pe.edu.eduspace.notificaciones.entity.Canal;
import pe.edu.eduspace.notificaciones.entity.EstadoNotificacion;
import pe.edu.eduspace.notificaciones.entity.Notificacion;
import pe.edu.eduspace.notificaciones.entity.PlantillaNotificacion;
import pe.edu.eduspace.notificaciones.repository.EventoProcesadoRepository;
import pe.edu.eduspace.notificaciones.repository.NotificacionInsertador;
import pe.edu.eduspace.notificaciones.service.*;

@Service
@Transactional
public class NotificacionOrquestadorImpl implements NotificacionOrquestador {

    private static final Logger log = LoggerFactory.getLogger(NotificacionOrquestadorImpl.class);

    /** Quien recibe: su usuario en auth-ms y, para el correo, la dirección. */
    private record Destinatario(Long usuarioId, String email) {
    }

    private final EventoProcesadoRepository eventoRepository;
    private final ContactosAlumnoService contactos;
    private final PlantillaService plantillas;
    private final PreferenciaService preferencias;
    private final DispositivoService dispositivos;
    private final NotificacionInsertador insertador;
    private final ApplicationEventPublisher publisher;
    private final PendienteCodec codec;

    public NotificacionOrquestadorImpl(EventoProcesadoRepository eventoRepository, ContactosAlumnoService contactos,
                                       PlantillaService plantillas, PreferenciaService preferencias,
                                       DispositivoService dispositivos, NotificacionInsertador insertador,
                                       ApplicationEventPublisher publisher, PendienteCodec codec) {
        this.eventoRepository = eventoRepository;
        this.contactos = contactos;
        this.plantillas = plantillas;
        this.preferencias = preferencias;
        this.dispositivos = dispositivos;
        this.insertador = insertador;
        this.publisher = publisher;
        this.codec = codec;
    }

    @Override
    public void procesar(EventoNotificable evento) {
        // idempotencia por eventId: se decide en la misma transacción que crea las notificaciones
        if (evento.eventId() != null && eventoRepository.registrarSiNoExiste(evento.eventId(), evento.eventType()) == 0) {
            log.info("component=NotificacionOrquestador eventType={} key={} status=DUPLICADO eventId={}",
                    evento.eventType(), evento.claveEvento(), evento.eventId());
            return;
        }
        if (evento.destino() == Destino.USUARIOS_DIRECTOS) {
            for (Long usuarioId : evento.usuarioIds().stream().distinct().toList()) {
                notificar(new Destinatario(usuarioId, null), evento, evento.datos(), null);
            }
            return;
        }
        for (Long alumnoId : evento.alumnoIds().stream().distinct().toList()) {
            ContactosResultado resultado = contactos.resolver(alumnoId);
            switch (resultado.estado()) {
                case DISPONIBLE -> notificarContactos(evento, alumnoId, resultado.datos());
                case NO_DISPONIBLE -> crearPendiente(evento, alumnoId);
                case NO_EXISTE -> log.warn("component=NotificacionOrquestador eventType={} key={} status=DESCARTADO alumnoId={} cause=ALUMNO_NO_EXISTE",
                        evento.eventType(), evento.claveEvento(), alumnoId);
            }
        }
    }

    @Override
    public ResultadoResolucion reintentarPendiente(EventoNotificable evento, Long alumnoId) {
        ContactosResultado resultado = contactos.resolver(alumnoId);
        return switch (resultado.estado()) {
            case DISPONIBLE -> {
                notificarContactos(evento, alumnoId, resultado.datos());
                yield ResultadoResolucion.RESUELTO;
            }
            case NO_DISPONIBLE -> ResultadoResolucion.PENDIENTE;
            case NO_EXISTE -> ResultadoResolucion.NO_EXISTE;
        };
    }

    private void notificarContactos(EventoNotificable evento, Long alumnoId, ContactosDto c) {
        Map<String, String> datos = new LinkedHashMap<>(evento.datos());
        datos.putIfAbsent("alumno", c.nombreCompleto());
        datos.put("alumnoId", String.valueOf(alumnoId));
        List<Destinatario> destinatarios = destinatarios(evento.destino(), c);
        if (destinatarios.isEmpty()) {
            log.warn("component=NotificacionOrquestador eventType={} key={} status=SIN_DESTINATARIOS alumnoId={}",
                    evento.eventType(), evento.claveEvento(), alumnoId);
        }
        for (Destinatario d : destinatarios) {
            notificar(d, evento, datos, alumnoId);
        }
    }

    /** Quién recibe según el destino; solo quienes tienen cuenta (usuario_id) pueden tener bandeja. */
    private List<Destinatario> destinatarios(Destino destino, ContactosDto c) {
        Map<Long, Destinatario> resultado = new LinkedHashMap<>();
        List<ApoderadoDto> apoderados = c.apoderadosOVacio().stream().filter(a -> a.usuarioId() != null).toList();
        switch (destino) {
            case ALUMNO_Y_APODERADOS -> {
                if (c.usuarioId() != null) {
                    resultado.put(c.usuarioId(), new Destinatario(c.usuarioId(), null));
                }
                apoderados.forEach(a -> resultado.merge(a.usuarioId(), new Destinatario(a.usuarioId(), a.email()), (x, y) -> x));
            }
            case APODERADOS -> apoderados.forEach(a -> resultado.put(a.usuarioId(), new Destinatario(a.usuarioId(), a.email())));
            case RESPONSABLE_PAGO -> {
                List<ApoderadoDto> elegidos = apoderados.stream().filter(a -> Boolean.TRUE.equals(a.esResponsablePago())).toList();
                if (elegidos.isEmpty()) {
                    elegidos = apoderados.stream().filter(a -> Boolean.TRUE.equals(a.esPrincipal())).toList();
                }
                if (elegidos.isEmpty()) {
                    elegidos = apoderados;
                }
                elegidos.forEach(a -> resultado.put(a.usuarioId(), new Destinatario(a.usuarioId(), a.email())));
            }
            case USUARIOS_DIRECTOS -> {
            }
        }
        return new ArrayList<>(resultado.values());
    }

    /** Una notificación por canal habilitado, con su plantilla; la restricción única evita los duplicados. */
    private void notificar(Destinatario destinatario, EventoNotificable evento, Map<String, String> datos, Long alumnoId) {
        for (Canal canal : Canal.values()) {
            Optional<PlantillaNotificacion> plantilla = plantillas.activa(evento.plantilla(), canal);
            if (plantilla.isEmpty()) {
                if (canal == Canal.IN_APP) {
                    log.warn("component=NotificacionOrquestador eventType={} key={} status=SIN_PLANTILLA plantilla={}",
                            evento.eventType(), evento.claveEvento(), evento.plantilla());
                }
                continue;
            }
            if (canal == Canal.EMAIL && (destinatario.email() == null || destinatario.email().isBlank())) {
                continue;
            }
            if (canal == Canal.PUSH && dispositivos.tokensActivos(destinatario.usuarioId()).isEmpty()) {
                continue;
            }
            if (!preferencias.habilitada(destinatario.usuarioId(), evento.plantilla(), canal)) {
                continue;
            }
            PlantillaService.Texto texto = plantillas.armar(plantilla.get(), datos);
            Map<String, Object> extra = new LinkedHashMap<>(datos);
            if (canal == Canal.EMAIL) {
                extra.put("email", destinatario.email());
            }
            Notificacion n = new Notificacion();
            n.setUsuarioId(destinatario.usuarioId());
            n.setTipo(evento.plantilla());
            n.setCanal(canal);
            n.setTitulo(texto.asunto().isBlank() ? "Notificación" : texto.asunto());
            n.setMensaje(texto.cuerpo());
            n.setDatosJson(codec.escribirDatos(extra));
            n.setEventoOrigen(evento.eventType());
            n.setEventoClave(evento.claveEvento());
            n.setEstado(canal == Canal.IN_APP ? EstadoNotificacion.ENVIADA : EstadoNotificacion.PENDIENTE);
            n.setEnviadaEn(canal == Canal.IN_APP ? LocalDateTime.now() : null);
            Optional<Long> id = insertador.insertar(n);
            if (id.isEmpty()) {
                log.info("component=NotificacionOrquestador eventType={} key={} status=YA_EXISTIA usuarioId={} canal={}",
                        evento.eventType(), evento.claveEvento(), destinatario.usuarioId(), canal);
            } else if (canal != Canal.IN_APP) {
                publisher.publishEvent(new NotificacionCreadaEvent(id.get()));
            }
        }
    }

    /** alumnos-ms no respondió: se guarda lo necesario para volver a resolver los destinatarios más tarde. */
    private void crearPendiente(EventoNotificable evento, Long alumnoId) {
        Notificacion n = new Notificacion();
        n.setUsuarioId(Notificacion.SIN_DESTINATARIO);
        n.setTipo(evento.plantilla());
        n.setCanal(Canal.IN_APP);
        n.setTitulo("Pendiente: destinatarios sin resolver");
        n.setMensaje("Se notificará cuando alumnos-ms responda (alumno " + alumnoId + ", evento " + evento.eventType() + ").");
        n.setDatosJson(codec.escribir(evento, alumnoId));
        n.setEventoOrigen(evento.eventType());
        n.setEventoClave(evento.claveEvento() + ":" + alumnoId);
        n.setEstado(EstadoNotificacion.PENDIENTE);
        boolean creada = insertador.insertar(n).isPresent();
        log.warn("component=NotificacionOrquestador eventType={} key={} status=PENDIENTE alumnoId={} nueva={}",
                evento.eventType(), evento.claveEvento(), alumnoId, creada);
    }
}
