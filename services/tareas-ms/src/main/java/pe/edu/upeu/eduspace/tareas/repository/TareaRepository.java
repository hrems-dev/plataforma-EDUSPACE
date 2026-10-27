package pe.edu.upeu.eduspace.tareas.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.edu.upeu.eduspace.tareas.entity.Tarea;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface TareaRepository extends JpaRepository<Tarea, Long> {

    @Query("""
        SELECT t FROM Tarea t
        WHERE (:seccionId IS NULL OR t.seccionId = :seccionId)
          AND (:cursoId IS NULL OR t.cursoId = :cursoId)
          AND (:periodoId IS NULL OR t.periodoId = :periodoId)
          AND (:docenteId IS NULL OR t.docenteId = :docenteId)
          AND (:estado IS NULL OR t.estado = :estado)
        """)
    Page<Tarea> buscar(@Param("seccionId") Long seccionId, @Param("cursoId") Long cursoId, @Param("periodoId") Long periodoId,
                       @Param("docenteId") Long docenteId, @Param("estado") String estado, Pageable pageable);

    List<Tarea> findBySeccionIdInAndEstadoOrderByFechaEntregaAsc(Collection<Long> seccionIds, String estado);

    List<Tarea> findBySeccionIdInAndEstadoInOrderByFechaEntregaDesc(Collection<Long> seccionIds, Collection<String> estados);

    /** Publicadas que vencen dentro de la ventana y aún sin recordatorio. */
    @Query("SELECT t FROM Tarea t WHERE t.estado = 'PUBLICADA' AND t.recordatorioEnviado = false "
         + "AND t.fechaEntrega > :ahora AND t.fechaEntrega <= :limite")
    List<Tarea> porVencer(@Param("ahora") LocalDateTime ahora, @Param("limite") LocalDateTime limite);

    /** Vencidas que no admiten entrega tardía: se cierran solas. */
    @Query("SELECT t FROM Tarea t WHERE t.estado = 'PUBLICADA' AND t.permiteTardia = false AND t.fechaEntrega < :ahora")
    List<Tarea> vencidasSinTardia(@Param("ahora") LocalDateTime ahora);
}
