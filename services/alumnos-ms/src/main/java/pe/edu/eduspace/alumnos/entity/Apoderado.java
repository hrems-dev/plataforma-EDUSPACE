package pe.edu.eduspace.alumnos.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "apoderados")
@Getter
@Setter
public class Apoderado {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    /** Id en auth-ms (rol APODERADO); número simple, sin relación. */
    private Long usuarioId;
    private String tipoDocumento = "DNI";
    private String numeroDocumento;
    private String nombres;
    private String apellidos;
    private String telefono;
    private String telefonoAlterno;
    private String email;
    private String direccion;
    private String ocupacion;
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
