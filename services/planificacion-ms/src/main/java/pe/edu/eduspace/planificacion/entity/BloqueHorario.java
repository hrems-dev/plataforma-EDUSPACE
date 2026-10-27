package pe.edu.eduspace.planificacion.entity;

import jakarta.persistence.*;
import java.time.LocalTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "bloques_horarios")
@Getter
@Setter
public class BloqueHorario {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING)
    private Turno turno;
    private Short orden;
    private LocalTime horaInicio;
    private LocalTime horaFin;
    private Boolean esReceso = false;
}
