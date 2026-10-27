package pe.edu.eduspace.alumnos.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.eduspace.alumnos.entity.AlumnoDocumento;

public interface AlumnoDocumentoRepository extends JpaRepository<AlumnoDocumento, Long> {
    List<AlumnoDocumento> findByAlumnoIdOrderBySubidoEnDesc(Long alumnoId);

    Optional<AlumnoDocumento> findByIdAndAlumnoId(Long id, Long alumnoId);
}
