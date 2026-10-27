package pe.edu.upeu.eduspace.pago.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pe.edu.upeu.eduspace.pago.entity.Tarifa;

import java.util.List;
import java.util.Optional;

@Repository
public interface TarifaRepository extends JpaRepository<Tarifa, Long> {

    List<Tarifa> findByAnioIdAndActivoTrue(Long anioId);

    @Query("""
        SELECT t FROM Tarifa t
        WHERE t.anioId = :anioId
          AND t.nivelId = :nivelId
          AND (t.gradoId = :gradoId OR t.gradoId IS NULL)
          AND t.concepto.id = :conceptoId
          AND t.activo = true
        ORDER BY t.gradoId DESC NULLS LAST
    """)
    List<Tarifa> findTarifasCandidatas(@Param("anioId") Long anioId,
                                      @Param("nivelId") Long nivelId,
                                      @Param("gradoId") Long gradoId,
                                      @Param("conceptoId") Long conceptoId);

    default Optional<Tarifa> findTarifaAplicable(Long anioId, Long nivelId, Long gradoId, Long conceptoId) {
        List<Tarifa> list = findTarifasCandidatas(anioId, nivelId, gradoId, conceptoId);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }
}

