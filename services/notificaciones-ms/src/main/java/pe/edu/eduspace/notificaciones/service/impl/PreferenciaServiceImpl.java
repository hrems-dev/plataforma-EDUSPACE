package pe.edu.eduspace.notificaciones.service.impl;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.eduspace.notificaciones.dto.PreferenciaRequest;
import pe.edu.eduspace.notificaciones.dto.PreferenciaResponse;
import pe.edu.eduspace.notificaciones.entity.Canal;
import pe.edu.eduspace.notificaciones.entity.PreferenciaNotificacion;
import pe.edu.eduspace.notificaciones.mapper.NotificacionMapper;
import pe.edu.eduspace.notificaciones.repository.PreferenciaRepository;
import pe.edu.eduspace.notificaciones.service.PreferenciaService;

@Service
@RequiredArgsConstructor
@Transactional
public class PreferenciaServiceImpl implements PreferenciaService {

    private final PreferenciaRepository repository;
    private final NotificacionMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public boolean habilitada(Long usuarioId, String tipo, Canal canal) {
        return repository.findByUsuarioIdAndTipoAndCanal(usuarioId, tipo, canal)
                .map(p -> Boolean.TRUE.equals(p.getHabilitada())).orElse(true);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PreferenciaResponse> listar(Long usuarioId) {
        return repository.findByUsuarioIdOrderByTipoAscCanalAsc(usuarioId).stream().map(mapper::toResponse).toList();
    }

    @Override
    public List<PreferenciaResponse> guardar(Long usuarioId, List<PreferenciaRequest> cambios) {
        for (PreferenciaRequest c : cambios) {
            PreferenciaNotificacion p = repository.findByUsuarioIdAndTipoAndCanal(usuarioId, c.tipo(), c.canal())
                    .orElseGet(() -> {
                        PreferenciaNotificacion nueva = new PreferenciaNotificacion();
                        nueva.setUsuarioId(usuarioId);
                        nueva.setTipo(c.tipo());
                        nueva.setCanal(c.canal());
                        return nueva;
                    });
            p.setHabilitada(c.habilitada());
            repository.save(p);
        }
        return listar(usuarioId);
    }
}
