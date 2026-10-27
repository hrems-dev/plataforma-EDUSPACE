package pe.edu.eduspace.planificacion.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.eduspace.planificacion.entity.Feriado;

public interface FeriadoRepository extends JpaRepository<Feriado, Long> {
    List<Feriado> findByAnioIdOrderByFecha(Long anioId);

    boolean existsByAnioIdAndFecha(Long anioId, LocalDate fecha);

    Optional<Feriado> findFirstByFecha(LocalDate fecha);
}
