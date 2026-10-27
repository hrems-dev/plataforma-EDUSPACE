package pe.edu.eduspace.notificaciones.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.eduspace.notificaciones.dto.DispositivoRequest;
import pe.edu.eduspace.notificaciones.dto.DispositivoResponse;
import pe.edu.eduspace.notificaciones.entity.DispositivoPush;
import pe.edu.eduspace.notificaciones.exception.ResourceNotFoundException;
import pe.edu.eduspace.notificaciones.mapper.NotificacionMapper;
import pe.edu.eduspace.notificaciones.repository.DispositivoPushRepository;
import pe.edu.eduspace.notificaciones.service.DispositivoService;

@Service
@RequiredArgsConstructor
@Transactional
public class DispositivoServiceImpl implements DispositivoService {

    private final DispositivoPushRepository repository;
    private final NotificacionMapper mapper;

    @Override
    public DispositivoResponse registrar(Long usuarioId, DispositivoRequest request) {
        DispositivoPush dispositivo = repository.findByToken(request.token()).orElseGet(DispositivoPush::new);
        dispositivo.setToken(request.token());
        dispositivo.setUsuarioId(usuarioId);
        dispositivo.setPlataforma(request.plataforma());
        dispositivo.setActivo(true);
        dispositivo.setUltimoUso(LocalDateTime.now());
        return mapper.toResponse(repository.save(dispositivo));
    }

    @Override
    public void desactivar(Long usuarioId, String token) {
        DispositivoPush dispositivo = repository.findByToken(token)
                .filter(d -> usuarioId.equals(d.getUsuarioId()))
                .orElseThrow(() -> new ResourceNotFoundException("El dispositivo no está registrado para este usuario"));
        dispositivo.setActivo(false);
        repository.save(dispositivo);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> tokensActivos(Long usuarioId) {
        return repository.findByUsuarioIdAndActivoTrue(usuarioId).stream().map(DispositivoPush::getToken).toList();
    }
}
