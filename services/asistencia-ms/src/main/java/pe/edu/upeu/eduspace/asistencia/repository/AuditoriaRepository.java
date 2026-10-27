package pe.edu.upeu.eduspace.asistencia.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.upeu.eduspace.asistencia.entity.AsistenciaAuditoria;

import java.util.List;

public interface AuditoriaRepository extends JpaRepository<AsistenciaAuditoria, Long> {

    List<AsistenciaAuditoria> findByDetalleIdOrderByCambiadoEnDesc(Long detalleId);
}
