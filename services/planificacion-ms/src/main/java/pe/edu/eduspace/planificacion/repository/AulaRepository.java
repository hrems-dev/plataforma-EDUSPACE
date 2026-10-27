package pe.edu.eduspace.planificacion.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.eduspace.planificacion.entity.Aula;
import pe.edu.eduspace.planificacion.entity.TipoAula;

public interface AulaRepository extends JpaRepository<Aula, Long> {
    List<Aula> findByTipoOrderByCodigo(TipoAula tipo);

    List<Aula> findAllByOrderByCodigo();

    boolean existsByCodigo(String codigo);

    boolean existsByCodigoAndIdNot(String codigo, Long id);
}
