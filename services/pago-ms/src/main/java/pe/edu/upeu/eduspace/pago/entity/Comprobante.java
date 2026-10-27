package pe.edu.upeu.eduspace.pago.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "comprobantes", uniqueConstraints = {
    @UniqueConstraint(name = "uk_comprobante_pago", columnNames = {"pago_id"}),
    @UniqueConstraint(name = "uk_comprobante_numero", columnNames = {"tipo", "serie", "numero"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Comprobante {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pago_id", nullable = false)
    private Pago pago;

    @Column(nullable = false, length = 10)
    private String tipo; // RECIBO, BOLETA

    @Column(nullable = false, length = 4)
    private String serie;

    @Column(nullable = false)
    private Long numero;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal total;

    @Column(nullable = false, length = 8)
    @Builder.Default
    private String estado = "EMITIDO"; // EMITIDO, ANULADO

    @Column(name = "ruta_pdf", length = 400)
    private String rutaPdf;

    @Column(name = "emitido_en", nullable = false)
    @Builder.Default
    private LocalDateTime emitidoEn = LocalDateTime.now();
}

