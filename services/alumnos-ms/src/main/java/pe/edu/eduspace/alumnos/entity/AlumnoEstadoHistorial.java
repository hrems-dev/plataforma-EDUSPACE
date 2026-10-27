package pe.edu.eduspace.alumnos.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "alumno_estado_historial")
@Getter
@Setter
public class AlumnoEstadoHistorial {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "alumno_id")
    private Alumno alumno;
    @Enumerated(EnumType.STRING)
    private EstadoAlumno estadoAnterior;
    @Enumerated(EnumType.STRING)
    private EstadoAlumno estadoNuevo;
    private String motivo;
    /** Quién hizo el cambio (usuario_id en auth-ms). */
    private Long usuarioId;
    private LocalDateTime cambiadoEn;

    @PrePersist
    void onCreate() {
        if (cambiadoEn == null) {
            cambiadoEn = LocalDateTime.now();
        }
    }
}
