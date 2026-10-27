package pe.edu.upeu.eduspace.examenes.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "examenes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Examen {

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
    private String instrucciones;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDateTime fechaInicio;

    @Column(name = "fecha_fin", nullable = false)
    private LocalDateTime fechaFin;

    @Column(name = "duracion_min", nullable = false)
    private Short duracionMin;

    @Column(name = "intentos_max", nullable = false)
    @Builder.Default
    private Short intentosMax = 1;

    @Column(name = "barajar_preguntas", nullable = false)
    @Builder.Default
    private Boolean barajarPreguntas = false;

    @Column(name = "puntaje_total", nullable = false, precision = 6, scale = 2)
    @Builder.Default
    private BigDecimal puntajeTotal = BigDecimal.ZERO;

    @Column(nullable = false, length = 15)
    @Builder.Default
    private String estado = "BORRADOR"; // BORRADOR, PUBLICADO, CERRADO, CALIFICADO

    @Column(name = "creado_en", nullable = false)
    @Builder.Default
    private LocalDateTime creadoEn = LocalDateTime.now();

    @Column(name = "publicado_en")
    private LocalDateTime publicadoEn;

    @OneToMany(mappedBy = "examen", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orden ASC")
    @Builder.Default
    private List<ExamenPregunta> preguntas = new ArrayList<>();
}

