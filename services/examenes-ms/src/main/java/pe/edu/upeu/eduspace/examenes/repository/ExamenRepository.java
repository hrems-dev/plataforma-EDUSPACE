package pe.edu.upeu.eduspace.examenes.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pe.edu.upeu.eduspace.examenes.entity.Examen;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ExamenRepository extends JpaRepository<Examen, Long> {

    @Query("""
        SELECT e FROM Examen e
        WHERE (:seccionId IS NULL OR e.seccionId = :seccionId)
          AND (:cursoId IS NULL OR e.cursoId = :cursoId)
          AND (:docenteId IS NULL OR e.docenteId = :docenteId)
          AND (:estado IS NULL OR e.estado = :estado)
        ORDER BY e.fechaInicio DESC
    """)
    Page<Examen> findExamenesFiltrados(@Param("seccionId") Long seccionId,
                                      @Param("cursoId") Long cursoId,
                                      @Param("docenteId") Long docenteId,
                                      @Param("estado") String estado,
                                      Pageable pageable);

    @Query("""
        SELECT e FROM Examen e
        WHERE e.seccionId = :seccionId
          AND e.estado = 'PUBLICADO'
          AND :ahora BETWEEN e.fechaInicio AND e.fechaFin
        ORDER BY e.fechaFin ASC
    """)
    List<Examen> findExamenesDisponiblesAlumno(@Param("seccionId") Long seccionId,
                                              @Param("ahora") LocalDateTime ahora);

    @Query("""
        SELECT e FROM Examen e
        WHERE e.estado = 'PUBLICADO'
          AND e.fechaFin < :ahora
    """)
    List<Examen> findExamenesVencidosParaCierre(@Param("ahora") LocalDateTime ahora);
}

