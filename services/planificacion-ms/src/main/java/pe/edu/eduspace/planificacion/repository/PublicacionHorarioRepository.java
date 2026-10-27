package pe.edu.eduspace.planificacion.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.eduspace.planificacion.entity.PublicacionHorario;

public interface PublicacionHorarioRepository extends JpaRepository<PublicacionHorario, Long> {
    Optional<PublicacionHorario> findFirstBySeccionIdOrderByVersionDesc(Long seccionId);

    List<PublicacionHorario> findBySeccionIdOrderByVersionDesc(Long seccionId);
}
