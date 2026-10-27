package pe.edu.upeu.eduspace.pago.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "descuentos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Descuento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false, length = 10)
    private String tipo; // PORCENTAJE, MONTO_FIJO

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valor;

    @Column(name = "aplica_a", nullable = false, length = 10)
    @Builder.Default
    private String aplicaA = "PENSION"; // MATRICULA, PENSION, TODO

    @Column(nullable = false)
    @Builder.Default
    private Boolean activo = true;
}

