package pe.edu.upeu.eduspace.matricula.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.edu.upeu.eduspace.matricula.entity.OutboxEvento;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface OutboxEventoRepository extends JpaRepository<OutboxEvento, UUID> {

    @Query(value = """
        SELECT * FROM outbox_eventos
        WHERE enviado_en IS NULL AND intentos < :maxIntentos
        ORDER BY creado_en ASC
        LIMIT 50
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<OutboxEvento> tomarLotePendiente(@Param("maxIntentos") int maxIntentos);

    @Modifying
    long deleteByEnviadoEnBefore(LocalDateTime limite);

    long countByEnviadoEnIsNull();
}
