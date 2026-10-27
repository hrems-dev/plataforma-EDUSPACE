package pe.edu.upeu.eduspace.pago.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pe.edu.upeu.eduspace.pago.entity.CajaSesion;

import java.util.Optional;

@Repository
public interface CajaSesionRepository extends JpaRepository<CajaSesion, Long> {

    Optional<CajaSesion> findFirstByCajeroUsuarioIdAndEstado(Long cajeroUsuarioId, String estado);

    @Query("SELECT COUNT(c) > 0 FROM CajaSesion c WHERE c.cajeroUsuarioId = :cajeroId AND c.estado = 'ABIERTA'")
    boolean existsCajaAbierta(@Param("cajeroId") Long cajeroId);
}

