package pe.edu.upeu.eduspace.pago.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pe.edu.upeu.eduspace.pago.entity.SerieComprobante;

import java.util.Optional;

@Repository
public interface SerieComprobanteRepository extends JpaRepository<SerieComprobante, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SerieComprobante s WHERE s.tipo = :tipo AND s.activa = true ORDER BY s.id ASC LIMIT 1")
    Optional<SerieComprobante> findFirstByTipoActivaForUpdate(@Param("tipo") String tipo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SerieComprobante s WHERE s.tipo = :tipo AND s.serie = :serie")
    Optional<SerieComprobante> findByTipoAndSerieForUpdate(@Param("tipo") String tipo, @Param("serie") String serie);
}

