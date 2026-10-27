package pe.edu.upeu.eduspace.calificaciones.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
@Table(name = "resultados_anuales")
public class ResultadoAnual {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "matricula_id", nullable = false)
    private Long matriculaId;

    @Column(name = "alumno_id", nullable = false)
    private Long alumnoId;

    @Column(name = "anio_id", nullable = false)
    private Long anioId;

    @Column(name = "curso_id", nullable = false)
    private Long cursoId;

    @Column(name = "promedio_final", nullable = false, precision = 5, scale = 2)
    private BigDecimal promedioFinal;

    @Column(name = "literal", length = 5)
    private String literal;

    @Column(name = "estado", nullable = false, length = 14)
    private String estado;

    @Column(name = "calculado_en", nullable = false)
    private LocalDateTime calculadoEn;
}

