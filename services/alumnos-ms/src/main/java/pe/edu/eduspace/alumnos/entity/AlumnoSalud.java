package pe.edu.eduspace.alumnos.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Ficha de salud básica: comparte la clave con {@link Alumno} (uno a uno). */
@Entity
@Table(name = "alumno_salud")
@Getter
@Setter
public class AlumnoSalud {
    @Id
    private Long alumnoId;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "alumno_id")
    private Alumno alumno;
    private String grupoSanguineo;
    private String alergias;
    private String observaciones;
    private String seguroMedico;
    private String contactoEmergenciaNombre;
    private String contactoEmergenciaTelefono;
    private LocalDateTime actualizadoEn;

    @PrePersist
    @PreUpdate
    void touch() {
        actualizadoEn = LocalDateTime.now();
    }
}
