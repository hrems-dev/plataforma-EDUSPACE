package pe.edu.eduspace.notificaciones.repository;

import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.edu.eduspace.notificaciones.entity.EventoProcesado;

public interface EventoProcesadoRepository extends JpaRepository<EventoProcesado, UUID> {

    /** Devuelve 1 si el evento era nuevo y 0 si ya estaba registrado (se decide en la misma transacción que las notificaciones). */
    @Modifying
    @Query(value = "INSERT INTO eventos_procesados (event_id, event_type) VALUES (:id, :tipo) ON CONFLICT DO NOTHING",
            nativeQuery = true)
    int registrarSiNoExiste(@Param("id") UUID id, @Param("tipo") String tipo);

    @Modifying
    @Query("delete from EventoProcesado e where e.procesadoEn < :limite")
    int borrarAnterioresA(@Param("limite") LocalDateTime limite);
}
