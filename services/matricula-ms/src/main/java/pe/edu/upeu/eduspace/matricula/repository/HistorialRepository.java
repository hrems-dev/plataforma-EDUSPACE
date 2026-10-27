package pe.edu.upeu.eduspace.matricula.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.upeu.eduspace.matricula.entity.MatriculaHistorial;

import java.util.List;

public interface HistorialRepository extends JpaRepository<MatriculaHistorial, Long> {

    List<MatriculaHistorial> findByMatriculaIdOrderByFechaDescIdDesc(Long matriculaId);
}
