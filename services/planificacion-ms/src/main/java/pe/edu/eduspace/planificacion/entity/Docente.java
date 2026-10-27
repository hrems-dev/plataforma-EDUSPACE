package pe.edu.eduspace.planificacion.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "docentes")
@Getter
@Setter
public class Docente {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    /** Id en auth-ms; número simple, sin relación. */
    private Long usuarioId;
    private String tipoDocumento = "DNI";
    private String numeroDocumento;
    private String nombres;
    private String apellidos;
    private String email;
    private String telefono;
    private String especialidad;
    @Enumerated(EnumType.STRING)
    private EstadoDocente estado = EstadoDocente.ACTIVO;
    private LocalDateTime creadoEn;
    private LocalDateTime actualizadoEn;

    public String nombreCompleto() {
        return apellidos + ", " + nombres;
    }

    @PrePersist
    void onCreate() {
        creadoEn = LocalDateTime.now();
        actualizadoEn = creadoEn;
    }

    @PreUpdate
    void onUpdate() {
        actualizadoEn = LocalDateTime.now();
    }
}
