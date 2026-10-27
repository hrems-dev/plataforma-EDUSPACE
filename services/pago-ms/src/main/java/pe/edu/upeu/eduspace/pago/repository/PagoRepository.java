package pe.edu.upeu.eduspace.pago.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pe.edu.upeu.eduspace.pago.entity.Pago;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PagoRepository extends JpaRepository<Pago, Long> {

    Optional<Pago> findByIdempotencyKey(String idempotencyKey);

    List<Pago> findByAlumnoIdOrderByPagadoEnDesc(Long alumnoId);

    List<Pago> findByCajaSesionId(Long cajaSesionId);

    @Query("SELECT COALESCE(SUM(p.montoTotal), 0) FROM Pago p WHERE p.cajaSesion.id = :cajaId AND p.estado = 'CONFIRMADO'")
    BigDecimal sumTotalByCajaSesionId(@Param("cajaId") Long cajaId);

    @Query("SELECT COALESCE(SUM(p.montoTotal), 0) FROM Pago p WHERE p.cajaSesion.id = :cajaId AND p.medio = 'EFECTIVO' AND p.estado = 'CONFIRMADO'")
    BigDecimal sumEfectivoByCajaSesionId(@Param("cajaId") Long cajaId);

    @Query("""
        SELECT p FROM Pago p
        WHERE p.pagadoEn BETWEEN :desde AND :hasta
          AND (:medio IS NULL OR p.medio = :medio)
          AND p.estado = 'CONFIRMADO'
        ORDER BY p.pagadoEn DESC
    """)
    Page<Pago> findReporteCobranza(@Param("desde") LocalDateTime desde,
                                  @Param("hasta") LocalDateTime hasta,
                                  @Param("medio") String medio,
                                  Pageable pageable);
}

