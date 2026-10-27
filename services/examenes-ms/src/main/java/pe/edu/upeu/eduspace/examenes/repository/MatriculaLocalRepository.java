package pe.edu.upeu.eduspace.examenes.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pe.edu.upeu.eduspace.examenes.entity.MatriculaLocal;

import java.util.List;
import java.util.Optional;

@Repository
public interface MatriculaLocalRepository extends JpaRepository<MatriculaLocal, Long> {

    List<MatriculaLocal> findByAlumnoId(Long alumnoId);

    List<MatriculaLocal> findBySeccionIdAndEstado(Long seccionId, String estado);

    Optional<MatriculaLocal> findByAlumnoIdAndSeccionIdAndEstado(Long alumnoId, Long seccionId, String estado);
}

