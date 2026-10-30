package pe.edu.eduspace.planificacion.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.edu.eduspace.planificacion.entity.Asignacion;

public interface AsignacionRepository extends JpaRepository<Asignacion, Long> {
    List<Asignacion> findBySeccionId(Long seccionId);

    List<Asignacion> findByDocenteId(Long docenteId);

    @Query("select a from Asignacion a where a.seccion.anio.id = :anioId order by a.seccion.id, a.curso.id")
    List<Asignacion> findByAnio(@Param("anioId") Long anioId);

    Optional<Asignacion> findBySeccionIdAndCursoId(Long seccionId, Long cursoId);

    boolean existsBySeccionIdAndCursoId(Long seccionId, Long cursoId);

    boolean existsByDocenteId(Long docenteId);

    boolean existsByDocenteIdAndSeccionIdAndCursoId(Long docenteId, Long seccionId, Long cursoId);

    @Query("""
            select a from Asignacion a
            where a.docente.id = :docenteId and (:anioId is null or a.seccion.anio.id = :anioId)
            """)
    List<Asignacion> findByDocenteAndAnio(@Param("docenteId") Long docenteId, @Param("anioId") Long anioId);

    @Query("""
            select count(a) > 0 from Asignacion a
            where a.seccion.grado.id = :gradoId and a.curso.id = :cursoId
            """)
    boolean existsByGradoAndCurso(@Param("gradoId") Long gradoId, @Param("cursoId") Long cursoId);
}
