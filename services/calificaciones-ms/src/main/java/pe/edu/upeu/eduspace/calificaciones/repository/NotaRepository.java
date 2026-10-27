package pe.edu.upeu.eduspace.calificaciones.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pe.edu.upeu.eduspace.calificaciones.entity.Nota;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotaRepository extends JpaRepository<Nota, Long> {

    List<Nota> findByPeriodoIdAndSeccionIdAndCursoId(Long periodoId, Long seccionId, Long cursoId);

    List<Nota> findByMatriculaIdAndPeriodoIdAndCursoId(Long matriculaId, Long periodoId, Long cursoId);

    List<Nota> findByMatriculaIdAndCriterioId(Long matriculaId, Long criterioId);

    Optional<Nota> findByMatriculaIdAndCriterioIdAndOrigenAndOrigenId(Long matriculaId, Long criterioId, String origen, Long origenId);

    List<Nota> findByAlumnoIdAndPeriodoId(Long alumnoId, Long periodoId);

    List<Nota> findByPeriodoIdAndSeccionId(Long periodoId, Long seccionId);

    List<Nota> findByCriterioId(Long criterioId);

    @Query("SELECT n FROM Nota n WHERE n.criterio.id = :criterioId AND n.matriculaId = :matriculaId")
    Optional<Nota> findByCriterioIdAndMatriculaId(@Param("criterioId") Long criterioId, @Param("matriculaId") Long matriculaId);

    @Query("SELECT n FROM Nota n WHERE n.criterio.id = :criterioId AND n.matriculaId = :matriculaId AND n.origen = :origen AND (:origenId IS NULL OR n.origenId = :origenId)")
    Optional<Nota> findByCriterioAndMatriculaAndOrigen(@Param("criterioId") Long criterioId,
                                                      @Param("matriculaId") Long matriculaId,
                                                      @Param("origen") String origen,
                                                      @Param("origenId") Long origenId);
}

