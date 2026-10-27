package pe.edu.upeu.eduspace.tareas.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.upeu.eduspace.tareas.entity.TareaAdjunto;

import java.util.List;

public interface TareaAdjuntoRepository extends JpaRepository<TareaAdjunto, Long> {

    List<TareaAdjunto> findByTareaIdOrderByIdAsc(Long tareaId);
}
