package pe.edu.upeu.eduspace.asistencia.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.upeu.eduspace.asistencia.entity.Justificacion;

import java.util.Collection;
import java.util.List;

public interface JustificacionRepository extends JpaRepository<Justificacion, Long> {

    Page<Justificacion> findByEstadoOrderByCreadoEnAsc(String estado, Pageable pageable);

    Page<Justificacion> findAllByOrderByCreadoEnDesc(Pageable pageable);

    List<Justificacion> findByAlumnoIdInOrderByCreadoEnDesc(Collection<Long> alumnoIds);
}
