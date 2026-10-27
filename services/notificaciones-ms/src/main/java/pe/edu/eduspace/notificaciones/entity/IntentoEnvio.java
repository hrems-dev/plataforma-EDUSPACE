package pe.edu.eduspace.notificaciones.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "intentos_envio")
@Getter
@Setter
public class IntentoEnvio {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "notificacion_id")
    private Notificacion notificacion;
    private Short numeroIntento;
    private Boolean exitoso;
    private String error;
    private LocalDateTime intentadoEn;

    @PrePersist
    void onCreate() {
        if (intentadoEn == null) {
            intentadoEn = LocalDateTime.now();
        }
    }
}
