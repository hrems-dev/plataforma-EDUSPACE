package pe.edu.eduspace.notificaciones.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "plantillas_notificacion")
@Getter
@Setter
public class PlantillaNotificacion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String codigo;
    @Enumerated(EnumType.STRING)
    private Canal canal;
    private String asunto;
    /** Texto con marcadores {{campo}}. */
    private String cuerpo;
    private Boolean activa = true;
    private LocalDateTime actualizadoEn;

    @PrePersist
    @PreUpdate
    void touch() {
        actualizadoEn = LocalDateTime.now();
    }
}
