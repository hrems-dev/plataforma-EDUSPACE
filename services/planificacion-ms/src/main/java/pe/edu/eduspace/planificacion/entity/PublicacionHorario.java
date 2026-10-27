package pe.edu.eduspace.planificacion.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "publicaciones_horario")
@Getter
@Setter
public class PublicacionHorario {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seccion_id")
    private Seccion seccion;
    private Integer version;
    /** usuario_id en auth-ms (sin relación). */
    private Long publicadoPor;
    private LocalDateTime publicadoEn;

    @PrePersist
    void onCreate() {
        if (publicadoEn == null) {
            publicadoEn = LocalDateTime.now();
        }
    }
}
