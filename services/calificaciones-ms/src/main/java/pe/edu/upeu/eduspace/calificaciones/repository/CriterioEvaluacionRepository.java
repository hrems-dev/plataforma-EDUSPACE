package pe.edu.upeu.eduspace.calificaciones.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pe.edu.upeu.eduspace.calificaciones.entity.CriterioEvaluacion;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface CriterioEvaluacionRepository extends JpaRepository<CriterioEvaluacion, Long> {

    List<CriterioEvaluacion> findByPeriodoIdAndSeccionIdAndCursoId(Long periodoId, Long seccionId, Long cursoId);

    List<CriterioEvaluacion> findByPeriodoIdAndSeccionIdAndCursoIdAndOrigenTipo(Long periodoId, Long seccionId, Long cursoId, String origenTipo);

    Optional<CriterioEvaluacion> findFirstByPeriodoIdAndSeccionIdAndCursoIdAndOrigenTipo(Long periodoId, Long seccionId, Long cursoId, String origenTipo);

    Optional<CriterioEvaluacion> findByPeriodoIdAndSeccionIdAndCursoIdAndNombre(Long periodoId, Long seccionId, Long cursoId, String nombre);

    @Query("SELECT COALESCE(SUM(c.peso), 0) FROM CriterioEvaluacion c WHERE c.periodoId = :periodoId AND c.seccionId = :seccionId AND c.cursoId = :cursoId")
    BigDecimal sumPesoByPeriodoAndSeccionAndCurso(@Param("periodoId") Long periodoId,
                                                  @Param("seccionId") Long seccionId,
                                                  @Param("cursoId") Long cursoId);

    @Query("SELECT DISTINCT c.cursoId FROM CriterioEvaluacion c WHERE c.periodoId = :periodoId AND c.seccionId = :seccionId")
    List<Long> findDistinctCursosByPeriodoAndSeccion(@Param("periodoId") Long periodoId,
                                                    @Param("seccionId") Long seccionId);
}

