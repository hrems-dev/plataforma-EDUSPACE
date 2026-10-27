package pe.edu.upeu.eduspace.pago.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pe.edu.upeu.eduspace.pago.entity.MatriculaLocal;

import java.util.List;
import java.util.Optional;

@Repository
public interface MatriculaLocalRepository extends JpaRepository<MatriculaLocal, Long> {

    List<MatriculaLocal> findByAlumnoId(Long alumnoId);

    List<MatriculaLocal> findBySeccionIdAndEstado(Long seccionId, String estado);

    Optional<MatriculaLocal> findByAlumnoIdAndAnioIdAndEstadoIn(Long alumnoId, Long anioId, List<String> estados);
}

