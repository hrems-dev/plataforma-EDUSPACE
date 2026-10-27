package pe.edu.eduspace.notificaciones.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "dispositivos_push")
@Getter
@Setter
public class DispositivoPush {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long usuarioId;
    private String token;
    @Enumerated(EnumType.STRING)
    private Plataforma plataforma;
    private Boolean activo = true;
    private LocalDateTime registradoEn;
    private LocalDateTime ultimoUso;

    @PrePersist
    void onCreate() {
        if (registradoEn == null) {
            registradoEn = LocalDateTime.now();
        }
    }
}
