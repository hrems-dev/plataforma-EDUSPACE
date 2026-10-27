package pe.edu.eduspace.notificaciones.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.eduspace.notificaciones.entity.Canal;
import pe.edu.eduspace.notificaciones.entity.PlantillaNotificacion;

public interface PlantillaRepository extends JpaRepository<PlantillaNotificacion, Long> {
    Optional<PlantillaNotificacion> findByCodigoAndCanal(String codigo, Canal canal);

    List<PlantillaNotificacion> findAllByOrderByCodigoAscCanalAsc();
}
