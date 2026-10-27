package pe.edu.upeu.eduspace.pago.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "series_comprobante", uniqueConstraints = {
    @UniqueConstraint(name = "uk_serie", columnNames = {"tipo", "serie"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SerieComprobante {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private String tipo; // RECIBO, BOLETA

    @Column(nullable = false, length = 4)
    private String serie;

    @Column(name = "ultimo_numero", nullable = false)
    @Builder.Default
    private Long ultimoNumero = 0L;

    @Column(nullable = false)
    @Builder.Default
    private Boolean activa = true;
}

