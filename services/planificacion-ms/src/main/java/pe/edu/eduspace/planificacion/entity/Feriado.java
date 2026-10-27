package pe.edu.eduspace.planificacion.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "feriados")
@Getter
@Setter
public class Feriado {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "anio_academico_id")
    private AnioAcademico anio;
    private LocalDate fecha;
    private String descripcion;
}
