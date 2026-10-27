package pe.edu.upeu.eduspace.matricula.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.upeu.eduspace.matricula.entity.CampaniaMatricula;

import java.util.List;
import java.util.Optional;

public interface CampaniaRepository extends JpaRepository<CampaniaMatricula, Long> {

    Optional<CampaniaMatricula> findFirstByAnioIdAndEstado(Long anioId, String estado);

    List<CampaniaMatricula> findByAnioIdOrderByFechaInicioDesc(Long anioId);

    List<CampaniaMatricula> findAllByOrderByFechaInicioDesc();

    boolean existsByAnioIdAndNombre(Long anioId, String nombre);
}
