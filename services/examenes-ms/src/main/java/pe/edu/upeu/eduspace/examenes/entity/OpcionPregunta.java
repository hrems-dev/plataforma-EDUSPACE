package pe.edu.upeu.eduspace.examenes.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "opciones_pregunta", uniqueConstraints = {
    @UniqueConstraint(name = "uk_opcion_orden", columnNames = {"pregunta_id", "orden"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OpcionPregunta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pregunta_id", nullable = false)
    private Pregunta pregunta;

    @Column(nullable = false, length = 500)
    private String texto;

    @Column(name = "es_correcta", nullable = false)
    @Builder.Default
    private Boolean esCorrecta = false;

    @Column(nullable = false)
    @Builder.Default
    private Short orden = 1;
}

