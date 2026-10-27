package pe.edu.eduspace.notificaciones.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.eduspace.notificaciones.entity.Canal;
import pe.edu.eduspace.notificaciones.entity.PreferenciaNotificacion;

public interface PreferenciaRepository extends JpaRepository<PreferenciaNotificacion, Long> {
    Optional<PreferenciaNotificacion> findByUsuarioIdAndTipoAndCanal(Long usuarioId, String tipo, Canal canal);

    List<PreferenciaNotificacion> findByUsuarioIdOrderByTipoAscCanalAsc(Long usuarioId);
}
