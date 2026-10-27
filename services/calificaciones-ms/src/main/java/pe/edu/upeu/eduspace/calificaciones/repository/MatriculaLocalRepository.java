package pe.edu.upeu.eduspace.calificaciones.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pe.edu.upeu.eduspace.calificaciones.entity.MatriculaLocal;

import java.util.List;
import java.util.Optional;

@Repository
public interface MatriculaLocalRepository extends JpaRepository<MatriculaLocal, Long> {

    List<MatriculaLocal> findBySeccionIdAndEstado(Long seccionId, String estado);

    List<MatriculaLocal> findBySeccionId(Long seccionId);

    List<MatriculaLocal> findByAlumnoId(Long alumnoId);

    Optional<MatriculaLocal> findByAlumnoIdAndAnioId(Long alumnoId, Long anioId);
}

