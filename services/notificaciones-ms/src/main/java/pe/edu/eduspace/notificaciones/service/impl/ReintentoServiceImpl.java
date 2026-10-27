package pe.edu.eduspace.notificaciones.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.eduspace.notificaciones.config.AppProperties;
import pe.edu.eduspace.notificaciones.entity.EstadoNotificacion;
import pe.edu.eduspace.notificaciones.entity.Notificacion;
import pe.edu.eduspace.notificaciones.repository.EventoProcesadoRepository;
import pe.edu.eduspace.notificaciones.repository.NotificacionRepository;
import pe.edu.eduspace.notificaciones.service.EnvioNotificacionService;
import pe.edu.eduspace.notificaciones.service.ReintentoService;

@Service
public class ReintentoServiceImpl implements ReintentoService {

    private static final Logger log = LoggerFactory.getLogger(ReintentoServiceImpl.class);

    private final NotificacionRepository repository;
    private final EventoProcesadoRepository eventoRepository;
    private final EnvioNotificacionService envio;
    private final AppProperties props;

    public ReintentoServiceImpl(NotificacionRepository repository, EventoProcesadoRepository eventoRepository,
                                EnvioNotificacionService envio, AppProperties props) {
        this.repository = repository;
        this.eventoRepository = eventoRepository;
        this.envio = envio;
        this.props = props;
    }

    @Override
    public int procesarPendientes() {
        List<Notificacion> lote = repository.pendientes(List.of(EstadoNotificacion.PENDIENTE, EstadoNotificacion.FALLIDA),
                props.reintentos().maxIntentos(), PageRequest.of(0, props.reintentos().lote()));
        int intentadas = 0;
        for (Notificacion n : lote) {
            try {
                envio.intentar(n.getId());
                intentadas++;
            } catch (RuntimeException ex) {
                log.error("component=ReintentoService eventType={} key={} status=ERROR cause={}", n.getTipo(), n.getId(), ex.getMessage());
            }
        }
        if (intentadas > 0) {
            log.info("component=ReintentoService eventType=Ciclo status=OK intentadas={}", intentadas);
        }
        return intentadas;
    }

    @Override
    @Transactional
    public int limpiarEventosProcesados() {
        int borrados = eventoRepository.borrarAnterioresA(LocalDateTime.now().minusDays(props.limpieza().diasEventosProcesados()));
        log.info("component=ReintentoService eventType=LimpiezaEventosProcesados status=OK borrados={}", borrados);
        return borrados;
    }
}
