package pe.edu.upeu.eduspace.calificaciones.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pe.edu.upeu.eduspace.calificaciones.entity.ResultadoAnual;

import java.util.List;
import java.util.Optional;

@Repository
public interface ResultadoAnualRepository extends JpaRepository<ResultadoAnual, Long> {

    Optional<ResultadoAnual> findByMatriculaIdAndCursoId(Long matriculaId, Long cursoId);

    List<ResultadoAnual> findByAlumnoIdAndAnioId(Long alumnoId, Long anioId);

    List<ResultadoAnual> findByMatriculaId(Long matriculaId);

    @Query("SELECT r FROM ResultadoAnual r WHERE r.matriculaId IN :matriculaIds")
    List<ResultadoAnual> findByMatriculaIdIn(@Param("matriculaIds") List<Long> matriculaIds);
}

