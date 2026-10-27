package pe.edu.upeu.eduspace.tareas.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "tareas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tarea {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "anio_id", nullable = false)
    private Long anioId;

    @Column(name = "periodo_id", nullable = false)
    private Long periodoId;

    @Column(name = "seccion_id", nullable = false)
    private Long seccionId;

    @Column(name = "curso_id", nullable = false)
    private Long cursoId;

    @Column(name = "docente_id", nullable = false)
    private Long docenteId;

    @Column(nullable = false, length = 200)
    private String titulo;

    @Column(columnDefinition = "TEXT")
    private String descripcion;

    @Column(name = "publicada_en")
    private LocalDateTime publicadaEn;

    @Column(name = "fecha_entrega", nullable = false)
    private LocalDateTime fechaEntrega;

    @Builder.Default
    @Column(name = "permite_tardia", nullable = false)
    private Boolean permiteTardia = false;

    @Builder.Default
    @Column(name = "puntaje_maximo", nullable = false, precision = 5, scale = 2)
    private BigDecimal puntajeMaximo = new BigDecimal("20");

    @Builder.Default
    @Column(nullable = false, length = 10)
    private String estado = "BORRADOR";

    @Builder.Default
    @Column(name = "creada_en", nullable = false)
    private LocalDateTime creadaEn = LocalDateTime.now();

    @Builder.Default
    @Column(name = "actualizada_en", nullable = false)
    private LocalDateTime actualizadaEn = LocalDateTime.now();

    @Builder.Default
    @Column(name = "recordatorio_enviado", nullable = false)
    private Boolean recordatorioEnviado = false;
}
