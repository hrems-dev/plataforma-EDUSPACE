package pe.edu.eduspace.alumnos.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.eduspace.alumnos.entity.AlumnoEstadoHistorial;

public interface AlumnoEstadoHistorialRepository extends JpaRepository<AlumnoEstadoHistorial, Long> {
    List<AlumnoEstadoHistorial> findByAlumnoIdOrderByCambiadoEnDescIdDesc(Long alumnoId);
}
