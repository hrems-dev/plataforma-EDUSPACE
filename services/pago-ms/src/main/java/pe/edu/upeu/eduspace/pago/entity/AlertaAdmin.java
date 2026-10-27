package pe.edu.upeu.eduspace.pago.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "alertas_admin")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertaAdmin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String tipo;

    @Column(name = "matricula_id")
    private Long matriculaId;

    @Column(name = "anio_id")
    private Long anioId;

    @Column(name = "nivel_id")
    private Long nivelId;

    @Column(name = "grado_id")
    private Long gradoId;

    @Column(nullable = false, length = 400)
    private String detalle;

    @Builder.Default
    @Column(nullable = false)
    private Boolean resuelta = false;

    @Builder.Default
    @Column(name = "creada_en", nullable = false)
    private LocalDateTime creadaEn = LocalDateTime.now();

    @Column(name = "resuelta_en")
    private LocalDateTime resueltaEn;
}
