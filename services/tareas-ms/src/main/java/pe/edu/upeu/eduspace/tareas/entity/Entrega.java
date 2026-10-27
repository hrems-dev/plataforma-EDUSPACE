package pe.edu.upeu.eduspace.tareas.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "entregas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Entrega {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tarea_id", nullable = false)
    private Long tareaId;

    @Column(name = "matricula_id", nullable = false)
    private Long matriculaId;

    @Column(name = "alumno_id", nullable = false)
    private Long alumnoId;

    @Column(length = 1000)
    private String comentario;

    @Builder.Default
    @Column(name = "entregada_en", nullable = false)
    private LocalDateTime entregadaEn = LocalDateTime.now();

    @Builder.Default
    @Column(name = "es_tardia", nullable = false)
    private Boolean esTardia = false;

    @Builder.Default
    @Column(nullable = false)
    private Short version = 1;

    @Builder.Default
    @Column(nullable = false, length = 10)
    private String estado = "ENTREGADA";

    @Column(precision = 5, scale = 2)
    private BigDecimal nota;

    @Column(columnDefinition = "TEXT")
    private String retroalimentacion;

    @Column(name = "calificada_en")
    private LocalDateTime calificadaEn;

    @Column(name = "calificada_por")
    private Long calificadaPor;
}
