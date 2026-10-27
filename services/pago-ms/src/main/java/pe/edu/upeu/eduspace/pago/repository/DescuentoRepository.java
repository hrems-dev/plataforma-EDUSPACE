package pe.edu.upeu.eduspace.pago.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pe.edu.upeu.eduspace.pago.entity.Descuento;

import java.util.List;

@Repository
public interface DescuentoRepository extends JpaRepository<Descuento, Long> {

    List<Descuento> findByActivoTrue();
}

