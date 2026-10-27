package pe.edu.eduspace.planificacion.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "aulas")
@Getter
@Setter
public class Aula {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String codigo;
    private String nombre;
    @Enumerated(EnumType.STRING)
    private TipoAula tipo = TipoAula.AULA;
    private Short capacidad;
    private Boolean activo = true;
}
