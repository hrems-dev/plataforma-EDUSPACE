package pe.edu.upeu.eduspace.pago.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "pagos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Pago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "alumno_id", nullable = false)
    private Long alumnoId;

    @Column(name = "pagador_nombre", length = 200)
    private String pagadorNombre;

    @Column(name = "pagador_documento", length = 20)
    private String pagadorDocumento;

    @Column(name = "monto_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal montoTotal;

    @Column(nullable = false, length = 15)
    private String medio; // EFECTIVO, TARJETA, YAPE_PLIN, TRANSFERENCIA, ONLINE_SIMULADO

    @Column(length = 80)
    private String referencia;

    @Column(nullable = false, length = 10)
    @Builder.Default
    private String estado = "CONFIRMADO"; // CONFIRMADO, ANULADO

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "caja_sesion_id")
    private CajaSesion cajaSesion;

    @Column(name = "registrado_por")
    private Long registradoPor;

    @Column(name = "pagado_en", nullable = false)
    @Builder.Default
    private LocalDateTime pagadoEn = LocalDateTime.now();

    @Column(name = "anulado_en")
    private LocalDateTime anuladoEn;

    @Column(name = "anulado_por")
    private Long anuladoPor;

    @Column(name = "motivo_anulacion", length = 300)
    private String motivoAnulacion;

    @Column(name = "idempotency_key", length = 80)
    private String idempotencyKey;

    @OneToMany(mappedBy = "pago", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PagoDetalle> detalles = new ArrayList<>();
}

