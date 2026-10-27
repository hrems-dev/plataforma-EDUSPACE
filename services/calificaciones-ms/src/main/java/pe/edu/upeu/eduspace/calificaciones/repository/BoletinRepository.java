package pe.edu.upeu.eduspace.calificaciones.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pe.edu.upeu.eduspace.calificaciones.entity.Boletin;

import java.util.List;
import java.util.Optional;

@Repository
public interface BoletinRepository extends JpaRepository<Boletin, Long> {

    Optional<Boletin> findFirstByMatriculaIdAndPeriodoIdAndEstado(Long matriculaId, Long periodoId, String estado);

    List<Boletin> findByMatriculaIdAndPeriodoIdOrderByVersionDesc(Long matriculaId, Long periodoId);

    Optional<Boletin> findFirstByMatriculaIdAndPeriodoIdIsNullAndEstado(Long matriculaId, String estado);

    List<Boletin> findByMatriculaIdAndPeriodoIdIsNullOrderByVersionDesc(Long matriculaId);

    List<Boletin> findByAlumnoIdOrderByGeneradoEnDesc(Long alumnoId);

    List<Boletin> findByMatriculaIdOrderByGeneradoEnDesc(Long matriculaId);
}

