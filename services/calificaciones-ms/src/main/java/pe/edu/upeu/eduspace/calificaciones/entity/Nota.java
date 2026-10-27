package pe.edu.upeu.eduspace.calificaciones.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "notas")
public class Nota {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "matricula_id", nullable = false)
    private Long matriculaId;

    @Column(name = "alumno_id", nullable = false)
    private Long alumnoId;

    @Column(name = "seccion_id", nullable = false)
    private Long seccionId;

    @Column(name = "curso_id", nullable = false)
    private Long cursoId;

    @Column(name = "periodo_id", nullable = false)
    private Long periodoId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "criterio_id", nullable = false)
    private CriterioEvaluacion criterio;

    @Column(name = "valor", nullable = false, precision = 5, scale = 2)
    private BigDecimal valor;

    @Column(name = "origen", nullable = false, length = 10)
    private String origen;

    @Column(name = "origen_id")
    private Long origenId;

    @Column(name = "registrado_por")
    private Long registradoPor;

    @Column(name = "registrado_en", nullable = false)
    private LocalDateTime registradoEn;

    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime actualizadoEn;
}

