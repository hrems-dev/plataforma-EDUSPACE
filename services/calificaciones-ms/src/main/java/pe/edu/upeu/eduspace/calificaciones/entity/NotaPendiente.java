package pe.edu.upeu.eduspace.calificaciones.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "notas_pendientes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotaPendiente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private String origen;

    @Column(name = "origen_id", nullable = false)
    private Long origenId;

    @Column(length = 200)
    private String titulo;

    @Column(name = "alumno_id")
    private Long alumnoId;

    @Column(name = "matricula_id", nullable = false)
    private Long matriculaId;

    @Column(name = "seccion_id")
    private Long seccionId;

    @Column(name = "curso_id", nullable = false)
    private Long cursoId;

    @Column(name = "periodo_id", nullable = false)
    private Long periodoId;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal valor;

    @Builder.Default
    @Column(nullable = false, length = 20)
    private String estado = "PENDIENTE";

    @Builder.Default
    @Column(name = "fecha_evento", nullable = false)
    private LocalDateTime fechaEvento = LocalDateTime.now();

    @Column(name = "aplicada_en")
    private LocalDateTime aplicadaEn;
}
