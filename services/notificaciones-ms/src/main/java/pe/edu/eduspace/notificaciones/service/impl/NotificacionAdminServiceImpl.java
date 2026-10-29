package pe.edu.eduspace.notificaciones.service.impl;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.eduspace.notificaciones.dto.NotificacionAdminResponse;
import pe.edu.eduspace.notificaciones.dto.NotificacionEditarRequest;
import pe.edu.eduspace.notificaciones.dto.PageResponse;
import pe.edu.eduspace.notificaciones.entity.Canal;
import pe.edu.eduspace.notificaciones.entity.EstadoNotificacion;
import pe.edu.eduspace.notificaciones.entity.Notificacion;
import pe.edu.eduspace.notificaciones.exception.ResourceNotFoundException;
import pe.edu.eduspace.notificaciones.repository.NotificacionRepository;
import pe.edu.eduspace.notificaciones.service.NotificacionAdminService;

@Service
@RequiredArgsConstructor
@Transactional
public class NotificacionAdminServiceImpl implements NotificacionAdminService {

    private final NotificacionRepository repository;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<NotificacionAdminResponse> listar(String tipo, Canal canal, EstadoNotificacion estado, Long usuarioId,
                                                          String texto, Pageable pageable) {
        Specification<Notificacion> filtro = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (tipo != null && !tipo.isBlank()) {
                p.add(cb.equal(root.get("tipo"), tipo.trim()));
            }
            if (canal != null) {
                p.add(cb.equal(root.get("canal"), canal));
            }
            if (estado != null) {
                p.add(cb.equal(root.get("estado"), estado));
            }
            if (usuarioId != null) {
                p.add(cb.equal(root.get("usuarioId"), usuarioId));
            }
            if (texto != null && !texto.isBlank()) {
                String like = "%" + texto.trim().toLowerCase() + "%";
                p.add(cb.or(cb.like(cb.lower(root.get("titulo")), like), cb.like(cb.lower(root.get("mensaje")), like)));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        Pageable orden = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(Sort.Order.desc("creadaEn"), Sort.Order.desc("id")));
        return PageResponse.of(repository.findAll(filtro, orden).map(this::respuesta));
    }

    @Override
    public NotificacionAdminResponse editar(Long id, NotificacionEditarRequest request) {
        Notificacion n = repository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Notificación", id));
        n.setTitulo(request.titulo().trim());
        n.setMensaje(request.mensaje().trim());
        return respuesta(repository.save(n));
    }

    @Override
    public void eliminar(Long id) {
        Notificacion n = repository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Notificación", id));
        repository.delete(n); // sus intentos de envío se borran en cascada (FK ON DELETE CASCADE)
    }

    private NotificacionAdminResponse respuesta(Notificacion n) {
        return new NotificacionAdminResponse(n.getId(), n.getUsuarioId(), n.getTipo(), n.getCanal(), n.getEstado(), n.getTitulo(),
                n.getMensaje(), n.getLeida(), n.getCreadaEn(), n.getEnviadaEn());
    }
}
