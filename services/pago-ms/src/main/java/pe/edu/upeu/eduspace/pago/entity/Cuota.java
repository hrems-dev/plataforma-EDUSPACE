package pe.edu.upeu.eduspace.pago.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "cuotas", uniqueConstraints = {
    @UniqueConstraint(name = "uk_cuota", columnNames = {"matricula_id", "concepto_id", "numero_cuota"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cuota {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "matricula_id", nullable = false)
    private MatriculaLocal matricula;

    @Column(name = "alumno_id", nullable = false)
    private Long alumnoId;

    @Column(name = "anio_id", nullable = false)
    private Long anioId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "concepto_id", nullable = false)
    private ConceptoPago concepto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tarifa_id")
    private Tarifa tarifa;

    @Column(nullable = false, length = 150)
    private String descripcion;

    @Column(name = "numero_cuota", nullable = false)
    @Builder.Default
    private Short numeroCuota = 1;

    @Column(name = "monto_base", nullable = false, precision = 10, scale = 2)
    private BigDecimal montoBase;

    @Column(nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal descuento = BigDecimal.ZERO;

    @Column(nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal mora = BigDecimal.ZERO;

    @Column(name = "monto_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal montoTotal;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal saldo;

    @Column(name = "fecha_vencimiento", nullable = false)
    private LocalDate fechaVencimiento;

    @Column(nullable = false, length = 10)
    @Builder.Default
    private String estado = "PENDIENTE"; // PENDIENTE, PARCIAL, PAGADA, VENCIDA, ANULADA

    @Column(name = "aviso_enviado", nullable = false)
    @Builder.Default
    private Boolean avisoEnviado = false;

    @Column(name = "creada_en", nullable = false)
    @Builder.Default
    private LocalDateTime creadaEn = LocalDateTime.now();

    @Column(name = "actualizada_en", nullable = false)
    @Builder.Default
    private LocalDateTime actualizadaEn = LocalDateTime.now();

    @Version
    @Column(nullable = false)
    @Builder.Default
    private Long version = 0L;
}

