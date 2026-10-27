package pe.edu.eduspace.planificacion.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.eduspace.planificacion.entity.AnioAcademico;
import pe.edu.eduspace.planificacion.entity.EstadoPeriodo;

public interface AnioAcademicoRepository extends JpaRepository<AnioAcademico, Long> {
    Optional<AnioAcademico> findFirstByEstado(EstadoPeriodo estado);

    boolean existsByNombre(String nombre);

    boolean existsByNombreAndIdNot(String nombre, Long id);

    boolean existsByEstado(EstadoPeriodo estado);
}
