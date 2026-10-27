package pe.edu.eduspace.planificacion.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "periodos")
@Getter
@Setter
public class Periodo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "anio_academico_id")
    private AnioAcademico anio;
    private Short numero;
    private String nombre;
    private LocalDate fechaInicio;
    private LocalDate fechaFin;
    @Enumerated(EnumType.STRING)
    private EstadoPeriodo estado = EstadoPeriodo.PLANIFICADO;
}
