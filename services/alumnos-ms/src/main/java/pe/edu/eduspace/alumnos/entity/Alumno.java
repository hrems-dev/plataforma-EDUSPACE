package pe.edu.eduspace.alumnos.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "alumnos")
@Getter
@Setter
public class Alumno {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String codigo;
    /** Id en auth-ms si el alumno tiene cuenta; número simple, sin relación. */
    private Long usuarioId;
    private String tipoDocumento = "DNI";
    private String numeroDocumento;
    private String nombres;
    private String apellidoPaterno;
    private String apellidoMaterno;
    private LocalDate fechaNacimiento;
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 1)
    private String sexo;
    private String nacionalidad;
    private String direccion;
    private String distrito;
    private String telefono;
    private String email;
    private String fotoUrl;
    private String colegioProcedencia;
    @Enumerated(EnumType.STRING)
    private EstadoAlumno estado = EstadoAlumno.ACTIVO;
    private LocalDateTime creadoEn;
    private LocalDateTime actualizadoEn;

    public String nombreCompleto() {
        String apellidos = apellidoMaterno == null || apellidoMaterno.isBlank()
                ? apellidoPaterno : apellidoPaterno + " " + apellidoMaterno;
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
