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

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "escalas_literales")
public class EscalaLiteral {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nivel_codigo", nullable = false, length = 15)
    private String nivelCodigo;

    @Column(name = "literal", nullable = false, length = 5)
    private String literal;

    @Column(name = "nota_min", nullable = false, precision = 5, scale = 2)
    private BigDecimal notaMin;

    @Column(name = "nota_max", nullable = false, precision = 5, scale = 2)
    private BigDecimal notaMax;

    @Column(name = "descripcion", length = 100)
    private String descripcion;
}

