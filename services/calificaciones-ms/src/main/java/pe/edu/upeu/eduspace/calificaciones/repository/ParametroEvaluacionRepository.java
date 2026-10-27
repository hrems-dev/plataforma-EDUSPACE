package pe.edu.upeu.eduspace.calificaciones.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pe.edu.upeu.eduspace.calificaciones.entity.ParametroEvaluacion;

@Repository
public interface ParametroEvaluacionRepository extends JpaRepository<ParametroEvaluacion, String> {
}

