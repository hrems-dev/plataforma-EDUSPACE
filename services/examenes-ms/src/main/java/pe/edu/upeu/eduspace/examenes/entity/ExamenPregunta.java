package pe.edu.upeu.eduspace.examenes.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "examen_preguntas", uniqueConstraints = {
    @UniqueConstraint(name = "uk_exam_preg", columnNames = {"examen_id", "pregunta_id"}),
    @UniqueConstraint(name = "uk_exam_orden", columnNames = {"examen_id", "orden"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExamenPregunta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "examen_id", nullable = false)
    private Examen examen;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pregunta_id", nullable = false)
    private Pregunta pregunta;

    @Column(nullable = false)
    private Short orden;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal puntaje;
}

