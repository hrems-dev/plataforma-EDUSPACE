package pe.edu.upeu.eduspace.examenes.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pe.edu.upeu.eduspace.examenes.entity.Intento;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface IntentoRepository extends JpaRepository<Intento, Long> {

    List<Intento> findByExamenIdAndMatriculaIdOrderByNumeroAsc(Long examenId, Long matriculaId);

    Optional<Intento> findFirstByExamenIdAndMatriculaIdAndEstado(Long examenId, Long matriculaId, String estado);

    short countByExamenIdAndMatriculaId(Long examenId, Long matriculaId);

    @Query("""
        SELECT i FROM Intento i
        WHERE i.estado = 'EN_CURSO'
          AND i.venceEn < :ahora
    """)
    List<Intento> findIntentosVencidosEnCurso(@Param("ahora") LocalDateTime ahora);

    List<Intento> findByExamenId(Long examenId);

    List<Intento> findByAlumnoIdOrderByIniciadoEnDesc(Long alumnoId);

    @Query("""
        SELECT DISTINCT i FROM Intento i
        JOIN i.respuestas r
        WHERE i.examen.id = :examenId
          AND r.requiereRevision = true
          AND r.puntajeObtenido IS NULL
    """)
    List<Intento> findIntentosPendientesRevision(@Param("examenId") Long examenId);
}

