package pe.edu.upeu.eduspace.pago.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pe.edu.upeu.eduspace.pago.entity.ConceptoPago;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConceptoPagoRepository extends JpaRepository<ConceptoPago, Long> {

    Optional<ConceptoPago> findByCodigo(String codigo);

    List<ConceptoPago> findByActivoTrue();
}

