package pe.edu.eduspace.notificaciones.service.impl;

import java.time.LocalDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.eduspace.notificaciones.dto.ConteoNoLeidasResponse;
import pe.edu.eduspace.notificaciones.dto.NotificacionResponse;
import pe.edu.eduspace.notificaciones.dto.PageResponse;
import pe.edu.eduspace.notificaciones.entity.Canal;
import pe.edu.eduspace.notificaciones.entity.Notificacion;
import pe.edu.eduspace.notificaciones.exception.ResourceNotFoundException;
import pe.edu.eduspace.notificaciones.repository.NotificacionRepository;
import pe.edu.eduspace.notificaciones.service.NotificacionService;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Transactional
public class NotificacionServiceImpl implements NotificacionService {

    private static final TypeReference<Map<String, Object>> MAPA = new TypeReference<>() {
    };

    private final NotificacionRepository repository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<NotificacionResponse> bandeja(Long usuarioId, Boolean leida, Pageable pageable) {
        Page<Notificacion> page = leida == null
                ? repository.findByUsuarioIdAndCanalOrderByCreadaEnDescIdDesc(usuarioId, Canal.IN_APP, pageable)
                : repository.findByUsuarioIdAndCanalAndLeidaOrderByCreadaEnDescIdDesc(usuarioId, Canal.IN_APP, leida, pageable);
        return PageResponse.of(page.map(this::respuesta));
    }

    @Override
    @Transactional(readOnly = true)
    public ConteoNoLeidasResponse conteoNoLeidas(Long usuarioId) {
        return new ConteoNoLeidasResponse(repository.countByUsuarioIdAndCanalAndLeidaFalse(usuarioId, Canal.IN_APP));
    }

    @Override
    public NotificacionResponse marcarLeida(Long usuarioId, Long id) {
        // una notificación ajena se trata como inexistente (404): no se revela que existe
        Notificacion n = repository.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(() -> ResourceNotFoundException.of("Notificación", id));
        if (!Boolean.TRUE.equals(n.getLeida())) {
            n.setLeida(true);
            n.setLeidaEn(LocalDateTime.now());
            repository.save(n);
        }
        return respuesta(n);
    }

    @Override
    public int marcarTodasLeidas(Long usuarioId) {
        return repository.marcarTodasLeidas(usuarioId, Canal.IN_APP);
    }

    private NotificacionResponse respuesta(Notificacion n) {
        return new NotificacionResponse(n.getId(), n.getTipo(), n.getTitulo(), n.getMensaje(), datos(n.getDatosJson()),
                n.getLeida(), n.getLeidaEn(), n.getCreadaEn());
    }

    private Map<String, Object> datos(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAPA);
        } catch (RuntimeException ex) {
            return Map.of();
        }
    }
}
