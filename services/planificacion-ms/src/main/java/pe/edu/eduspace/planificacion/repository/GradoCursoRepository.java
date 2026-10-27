package pe.edu.eduspace.planificacion.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.eduspace.planificacion.entity.GradoCurso;

public interface GradoCursoRepository extends JpaRepository<GradoCurso, Long> {
    List<GradoCurso> findByGradoIdOrderByCursoNombre(Long gradoId);

    Optional<GradoCurso> findByGradoIdAndCursoId(Long gradoId, Long cursoId);

    boolean existsByGradoIdAndCursoId(Long gradoId, Long cursoId);

    long countByCursoId(Long cursoId);
}
