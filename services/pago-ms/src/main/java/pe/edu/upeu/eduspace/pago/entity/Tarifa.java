package pe.edu.upeu.eduspace.pago.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "tarifas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tarifa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "anio_id", nullable = false)
    private Long anioId;

    @Column(name = "nivel_id", nullable = false)
    private Long nivelId;

    @Column(name = "grado_id")
    private Long gradoId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "concepto_id", nullable = false)
    private ConceptoPago concepto;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal monto;

    @Column(name = "num_cuotas", nullable = false)
    @Builder.Default
    private Short numCuotas = 1;

    @Column(name = "mes_inicio", nullable = false)
    @Builder.Default
    private Short mesInicio = 3;

    @Column(name = "dia_vencimiento", nullable = false)
    @Builder.Default
    private Short diaVencimiento = 5;

    @Column(nullable = false)
    @Builder.Default
    private Boolean activo = true;
}

