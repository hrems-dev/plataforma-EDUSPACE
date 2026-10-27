package pe.edu.upeu.eduspace.examenes.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "intentos", uniqueConstraints = {
    @UniqueConstraint(name = "uk_intento", columnNames = {"examen_id", "matricula_id", "numero"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Intento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "examen_id", nullable = false)
    private Examen examen;

    @Column(name = "matricula_id", nullable = false)
    private Long matriculaId;

    @Column(name = "alumno_id", nullable = false)
    private Long alumnoId;

    @Column(nullable = false)
    @Builder.Default
    private Short numero = 1;

    @Column(name = "iniciado_en", nullable = false)
    @Builder.Default
    private LocalDateTime iniciadoEn = LocalDateTime.now();

    @Column(name = "vence_en", nullable = false)
    private LocalDateTime venceEn;

    @Column(name = "enviado_en")
    private LocalDateTime enviadoEn;

    @Column(nullable = false, length = 12)
    @Builder.Default
    private String estado = "EN_CURSO"; // EN_CURSO, ENVIADO, CALIFICADO, VENCIDO

    @Column(name = "puntaje_obtenido", precision = 6, scale = 2)
    private BigDecimal puntajeObtenido;

    @Column(precision = 5, scale = 2)
    private BigDecimal nota; // Nota oficial 0-20

    @Column(name = "calificado_en")
    private LocalDateTime calificadoEn;

    @OneToMany(mappedBy = "intento", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Respuesta> respuestas = new ArrayList<>();
}

