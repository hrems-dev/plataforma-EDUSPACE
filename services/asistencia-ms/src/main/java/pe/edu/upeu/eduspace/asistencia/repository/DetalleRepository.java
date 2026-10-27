package pe.edu.upeu.eduspace.asistencia.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.edu.upeu.eduspace.asistencia.entity.AsistenciaDetalle;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DetalleRepository extends JpaRepository<AsistenciaDetalle, Long> {

    List<AsistenciaDetalle> findBySesionIdOrderByIdAsc(Long sesionId);

    Optional<AsistenciaDetalle> findBySesionIdAndMatriculaId(Long sesionId, Long matriculaId);

    @Query("""
        SELECT d FROM AsistenciaDetalle d JOIN FETCH d.sesion s
        WHERE d.alumnoId = :alumnoId AND s.fecha BETWEEN :desde AND :hasta
        ORDER BY s.fecha DESC, d.id DESC
        """)
    List<AsistenciaDetalle> delAlumnoEnRango(@Param("alumnoId") Long alumnoId, @Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta);

    @Query("""
        SELECT d FROM AsistenciaDetalle d JOIN FETCH d.sesion s
        WHERE s.seccionId = :seccionId AND s.fecha BETWEEN :desde AND :hasta
        """)
    List<AsistenciaDetalle> deSeccionEnRango(@Param("seccionId") Long seccionId, @Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta);

    /** Faltas de una matrícula dentro de un rango, para convertirlas en JUSTIFICADO. */
    @Query("""
        SELECT d FROM AsistenciaDetalle d JOIN FETCH d.sesion s
        WHERE d.matriculaId = :matriculaId AND d.estado = 'FALTA' AND s.fecha BETWEEN :desde AND :hasta
        """)
    List<AsistenciaDetalle> faltasDeMatriculaEnRango(@Param("matriculaId") Long matriculaId, @Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta);
}
