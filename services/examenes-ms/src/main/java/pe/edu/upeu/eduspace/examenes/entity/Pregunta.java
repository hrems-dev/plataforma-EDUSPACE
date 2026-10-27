package pe.edu.upeu.eduspace.examenes.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "preguntas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Pregunta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "curso_id", nullable = false)
    private Long cursoId;

    @Column(name = "docente_id", nullable = false)
    private Long docenteId;

    @Column(length = 100)
    private String tema;

    @Column(nullable = false, length = 20)
    private String tipo; // OPCION_UNICA, OPCION_MULTIPLE, VERDADERO_FALSO, ABIERTA

    @Column(nullable = false, columnDefinition = "TEXT")
    private String enunciado;

    @Column(nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal puntaje = BigDecimal.ONE;

    @Column(nullable = false)
    @Builder.Default
    private Boolean activa = true;

    @Column(name = "creada_en", nullable = false)
    @Builder.Default
    private LocalDateTime creadaEn = LocalDateTime.now();

    @OneToMany(mappedBy = "pregunta", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orden ASC")
    @Builder.Default
    private List<OpcionPregunta> opciones = new ArrayList<>();
}

