package pe.edu.eduspace.planificacion.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.eduspace.planificacion.entity.Curso;

public interface CursoRepository extends JpaRepository<Curso, Long> {
    List<Curso> findAllByOrderByNombre();

    List<Curso> findByIdIn(List<Long> ids);

    boolean existsByCodigo(String codigo);

    boolean existsByCodigoAndIdNot(String codigo, Long id);
}
