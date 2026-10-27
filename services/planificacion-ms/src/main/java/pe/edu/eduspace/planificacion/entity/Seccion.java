package pe.edu.eduspace.planificacion.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "secciones")
@Getter
@Setter
public class Seccion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "anio_academico_id")
    private AnioAcademico anio;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grado_id")
    private Grado grado;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "aula_id")
    private Aula aula;
    private String nombre;
    @Enumerated(EnumType.STRING)
    private Turno turno = Turno.MANANA;
    private Short capacidad = 30;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tutor_docente_id")
    private Docente tutor;
    private Boolean activo = true;
}
