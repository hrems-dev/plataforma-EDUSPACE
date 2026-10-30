package pe.edu.upeu.eduspace.tareas.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.upeu.eduspace.tareas.entity.Material;

import java.util.List;

public interface MaterialRepository extends JpaRepository<Material, Long> {

    List<Material> findBySeccionIdAndCursoIdOrderByCreadoEnDescIdDesc(Long seccionId, Long cursoId);
}
