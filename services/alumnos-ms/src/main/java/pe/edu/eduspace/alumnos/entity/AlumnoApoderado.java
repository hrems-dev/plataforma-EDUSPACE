package pe.edu.eduspace.alumnos.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "alumno_apoderado")
@Getter
@Setter
public class AlumnoApoderado {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "alumno_id")
    private Alumno alumno;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "apoderado_id")
    private Apoderado apoderado;
    @Enumerated(EnumType.STRING)
    private Parentesco parentesco;
    private Boolean esPrincipal = false;
    private Boolean esResponsablePago = false;
    private Boolean viveConAlumno = true;
    private Boolean puedeRecoger = true;
}
