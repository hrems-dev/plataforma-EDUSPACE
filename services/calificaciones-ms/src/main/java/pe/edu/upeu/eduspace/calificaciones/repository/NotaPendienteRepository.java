package pe.edu.upeu.eduspace.calificaciones.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.edu.upeu.eduspace.calificaciones.entity.NotaPendiente;

import java.util.List;
import java.util.Optional;

public interface NotaPendienteRepository extends JpaRepository<NotaPendiente, Long> {

    Optional<NotaPendiente> findByMatriculaIdAndOrigenAndOrigenId(Long matriculaId, String origen, Long origenId);

    @Query("""
        SELECT p FROM NotaPendiente p
        WHERE p.estado <> 'APLICADA'
          AND (:seccionId IS NULL OR p.seccionId = :seccionId)
          AND (:cursoId IS NULL OR p.cursoId = :cursoId)
        ORDER BY p.fechaEvento DESC
        """)
    List<NotaPendiente> findAbiertas(@Param("seccionId") Long seccionId, @Param("cursoId") Long cursoId);

    List<NotaPendiente> findByPeriodoIdAndSeccionIdAndCursoIdAndOrigenAndEstado(
        Long periodoId, Long seccionId, Long cursoId, String origen, String estado);
}
