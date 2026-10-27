package pe.edu.eduspace.notificaciones.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.eduspace.notificaciones.entity.DispositivoPush;

public interface DispositivoPushRepository extends JpaRepository<DispositivoPush, Long> {
    Optional<DispositivoPush> findByToken(String token);

    List<DispositivoPush> findByUsuarioIdAndActivoTrue(Long usuarioId);
}
