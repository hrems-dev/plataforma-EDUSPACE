package pe.edu.upeu.eduspace.tareas.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.upeu.eduspace.tareas.entity.MatriculaLocal;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MatriculaLocalRepository extends JpaRepository<MatriculaLocal, Long> {

    List<MatriculaLocal> findBySeccionIdAndEstadoOrderByAlumnoNombre(Long seccionId, String estado);

    List<MatriculaLocal> findBySeccionId(Long seccionId);

    Optional<MatriculaLocal> findFirstByAlumnoIdAndEstadoOrderByAnioIdDesc(Long alumnoId, String estado);

    List<MatriculaLocal> findByAlumnoIdIn(Collection<Long> alumnoIds);
}
