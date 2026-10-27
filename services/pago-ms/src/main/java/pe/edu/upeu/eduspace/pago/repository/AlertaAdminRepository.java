package pe.edu.upeu.eduspace.pago.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.upeu.eduspace.pago.entity.AlertaAdmin;

import java.util.List;

public interface AlertaAdminRepository extends JpaRepository<AlertaAdmin, Long> {

    List<AlertaAdmin> findByResueltaOrderByCreadaEnDesc(Boolean resuelta);

    boolean existsByTipoAndMatriculaIdAndResueltaFalse(String tipo, Long matriculaId);

    List<AlertaAdmin> findByTipoAndAnioIdAndNivelIdAndResueltaFalse(String tipo, Long anioId, Long nivelId);
}
