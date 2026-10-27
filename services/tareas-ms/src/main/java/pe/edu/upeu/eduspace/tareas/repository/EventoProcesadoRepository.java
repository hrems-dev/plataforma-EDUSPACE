package pe.edu.upeu.eduspace.tareas.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pe.edu.upeu.eduspace.tareas.entity.EventoProcesado;

import java.time.LocalDateTime;
import java.util.UUID;

@Repository
public interface EventoProcesadoRepository extends JpaRepository<EventoProcesado, UUID> {

    boolean existsByEventId(UUID eventId);

    long deleteByProcesadoEnBefore(LocalDateTime fechaLimite);
}

