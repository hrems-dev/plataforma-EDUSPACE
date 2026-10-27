package pe.edu.upeu.eduspace.pago.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "cajas_sesion")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CajaSesion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cajero_usuario_id", nullable = false)
    private Long cajeroUsuarioId;

    @Column(name = "apertura_en", nullable = false)
    @Builder.Default
    private LocalDateTime aperturaEn = LocalDateTime.now();

    @Column(name = "monto_apertura", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal montoApertura = BigDecimal.ZERO;

    @Column(name = "cierre_en")
    private LocalDateTime cierreEn;

    @Column(name = "monto_cierre_declarado", precision = 10, scale = 2)
    private BigDecimal montoCierreDeclarado;

    @Column(name = "monto_cierre_sistema", precision = 10, scale = 2)
    private BigDecimal montoCierreSistema;

    @Column(precision = 10, scale = 2)
    private BigDecimal diferencia;

    @Column(nullable = false, length = 8)
    @Builder.Default
    private String estado = "ABIERTA"; // ABIERTA, CERRADA
}

