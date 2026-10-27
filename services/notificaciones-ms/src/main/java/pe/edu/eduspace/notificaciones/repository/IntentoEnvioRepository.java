package pe.edu.eduspace.notificaciones.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.eduspace.notificaciones.entity.IntentoEnvio;

public interface IntentoEnvioRepository extends JpaRepository<IntentoEnvio, Long> {
    long countByNotificacionId(Long notificacionId);
}
