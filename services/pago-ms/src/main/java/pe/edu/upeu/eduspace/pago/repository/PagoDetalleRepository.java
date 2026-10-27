package pe.edu.upeu.eduspace.pago.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pe.edu.upeu.eduspace.pago.entity.PagoDetalle;

import java.util.List;

@Repository
public interface PagoDetalleRepository extends JpaRepository<PagoDetalle, Long> {

    List<PagoDetalle> findByPagoId(Long pagoId);

    List<PagoDetalle> findByCuotaId(Long cuotaId);
}

