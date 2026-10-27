package pe.edu.upeu.eduspace.matricula.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import pe.edu.upeu.eduspace.matricula.entity.EventoProcesado;

import java.time.LocalDateTime;
import java.util.UUID;

public interface EventoProcesadoRepository extends JpaRepository<EventoProcesado, UUID> {

    boolean existsByEventId(UUID eventId);

    @Modifying
    long deleteByProcesadoEnBefore(LocalDateTime limite);
}
