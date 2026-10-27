package pe.edu.eduspace.planificacion.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.edu.eduspace.planificacion.entity.Seccion;

public interface SeccionRepository extends JpaRepository<Seccion, Long> {
    @Query("""
            select s from Seccion s
            where (:anioId is null or s.anio.id = :anioId) and (:gradoId is null or s.grado.id = :gradoId)
            order by s.grado.id, s.nombre
            """)
    List<Seccion> buscar(@Param("anioId") Long anioId, @Param("gradoId") Long gradoId);

    boolean existsByAnioIdAndGradoIdAndNombre(Long anioId, Long gradoId, String nombre);

    boolean existsByAnioIdAndGradoIdAndNombreAndIdNot(Long anioId, Long gradoId, String nombre, Long id);

    long countByAulaId(Long aulaId);

    long countByAulaIdAndActivoTrue(Long aulaId);

    long countByTutorId(Long docenteId);
}
