package pe.edu.upeu.eduspace.calificaciones.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pe.edu.upeu.eduspace.calificaciones.entity.EventoProcesado;

import java.time.LocalDateTime;
import java.util.UUID;

@Repository
public interface EventoProcesadoRepository extends JpaRepository<EventoProcesado, UUID> {

    boolean existsByEventId(UUID eventId);

    @Modifying
    @Query("DELETE FROM EventoProcesado e WHERE e.procesadoEn < :limite")
    int deleteAntiguos(@Param("limite") LocalDateTime limite);
}

