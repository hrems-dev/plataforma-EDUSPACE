package pe.edu.eduspace.planificacion.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.eduspace.planificacion.entity.Nivel;

public interface NivelRepository extends JpaRepository<Nivel, Long> {
    List<Nivel> findAllByOrderByOrden();
}
