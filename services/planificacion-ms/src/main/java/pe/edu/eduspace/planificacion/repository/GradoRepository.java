package pe.edu.eduspace.planificacion.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.eduspace.planificacion.entity.Grado;

public interface GradoRepository extends JpaRepository<Grado, Long> {
    List<Grado> findByNivelIdOrderByNumero(Long nivelId);

    List<Grado> findAllByOrderByNivelOrdenAscNumeroAsc();
}
