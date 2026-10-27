package pe.edu.eduspace.notificaciones.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/** Idempotencia por {@code eventId}: un evento ya procesado no vuelve a generar notificaciones. */
@Entity
@Table(name = "eventos_procesados")
@Getter
@Setter
public class EventoProcesado {
    @Id
    private UUID eventId;
    private String eventType;
    private LocalDateTime procesadoEn;
}
