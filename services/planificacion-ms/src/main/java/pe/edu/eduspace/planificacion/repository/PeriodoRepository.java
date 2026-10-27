package pe.edu.eduspace.planificacion.repository;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.edu.eduspace.planificacion.entity.EstadoPeriodo;
import pe.edu.eduspace.planificacion.entity.Periodo;

public interface PeriodoRepository extends JpaRepository<Periodo, Long> {
    List<Periodo> findByAnioIdOrderByNumero(Long anioId);

    boolean existsByAnioIdAndNumero(Long anioId, Short numero);

    boolean existsByAnioIdAndNumeroAndIdNot(Long anioId, Short numero, Long id);

    boolean existsByAnioIdAndEstadoAndIdNot(Long anioId, EstadoPeriodo estado, Long id);

    @Query("""
            select count(p) > 0 from Periodo p
            where p.anio.id = :anioId and (:excluirId is null or p.id <> :excluirId)
              and p.fechaInicio <= :fin and p.fechaFin >= :inicio
            """)
    boolean haySolapamiento(@Param("anioId") Long anioId, @Param("inicio") LocalDate inicio,
                            @Param("fin") LocalDate fin, @Param("excluirId") Long excluirId);
}
