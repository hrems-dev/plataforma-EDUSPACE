package pe.edu.upeu.eduspace.tareas.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.edu.upeu.eduspace.tareas.entity.Entrega;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EntregaRepository extends JpaRepository<Entrega, Long> {

    Optional<Entrega> findByTareaIdAndMatriculaId(Long tareaId, Long matriculaId);

    List<Entrega> findByTareaIdOrderByEntregadaEnAsc(Long tareaId);

    List<Entrega> findByAlumnoIdIn(Collection<Long> alumnoIds);

    long countByTareaId(Long tareaId);

    long countByTareaIdAndEstado(Long tareaId, String estado);

    @Query("SELECT e FROM Entrega e WHERE e.tareaId = :tareaId AND e.estado = 'ENTREGADA'")
    List<Entrega> sinCalificar(@Param("tareaId") Long tareaId);
}
