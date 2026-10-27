package pe.edu.upeu.eduspace.calificaciones.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pe.edu.upeu.eduspace.calificaciones.entity.PromedioPeriodo;

import java.util.List;
import java.util.Optional;

@Repository
public interface PromedioPeriodoRepository extends JpaRepository<PromedioPeriodo, Long> {

    Optional<PromedioPeriodo> findByMatriculaIdAndCursoIdAndPeriodoId(Long matriculaId, Long cursoId, Long periodoId);

    List<PromedioPeriodo> findByPeriodoIdAndSeccionIdAndCursoId(Long periodoId, Long seccionId, Long cursoId);

    List<PromedioPeriodo> findByPeriodoIdAndSeccionId(Long periodoId, Long seccionId);

    List<PromedioPeriodo> findByMatriculaIdAndPeriodoId(Long matriculaId, Long periodoId);

    List<PromedioPeriodo> findByMatriculaId(Long matriculaId);

    List<PromedioPeriodo> findByAlumnoIdAndPeriodoId(Long alumnoId, Long periodoId);

    List<PromedioPeriodo> findByAlumnoId(Long alumnoId);
}

