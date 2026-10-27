package pe.edu.upeu.eduspace.asistencia.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.edu.upeu.eduspace.asistencia.entity.SesionAsistencia;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SesionRepository extends JpaRepository<SesionAsistencia, Long> {

    @Query("""
        SELECT s FROM SesionAsistencia s
        WHERE s.seccionId = :seccionId AND s.fecha = :fecha AND s.tipo = :tipo
          AND ((:cursoId IS NULL AND s.cursoId IS NULL) OR s.cursoId = :cursoId)
          AND ((:bloqueId IS NULL AND s.bloqueId IS NULL) OR s.bloqueId = :bloqueId)
        """)
    Optional<SesionAsistencia> buscarUnica(@Param("seccionId") Long seccionId, @Param("fecha") LocalDate fecha,
                                           @Param("tipo") String tipo, @Param("cursoId") Long cursoId,
                                           @Param("bloqueId") Long bloqueId);

    List<SesionAsistencia> findBySeccionIdAndFechaOrderByTipoAscIdAsc(Long seccionId, LocalDate fecha);

    List<SesionAsistencia> findByEstadoAndFechaBefore(String estado, LocalDate fecha);
}
