package pe.edu.upeu.eduspace.matricula.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.upeu.eduspace.matricula.entity.MatriculaRequisito;

import java.util.List;
import java.util.Optional;

public interface RequisitoRepository extends JpaRepository<MatriculaRequisito, Long> {

    List<MatriculaRequisito> findByMatriculaIdOrderByTipo(Long matriculaId);

    Optional<MatriculaRequisito> findByMatriculaIdAndTipo(Long matriculaId, String tipo);
}
