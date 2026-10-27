package pe.edu.upeu.eduspace.matricula.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "matriculas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Matricula {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 25, unique = true)
    private String codigo;

    @Column(name = "alumno_id", nullable = false)
    private Long alumnoId;

    @Column(name = "alumno_nombre", nullable = false, length = 220)
    private String alumnoNombre;

    @Column(name = "anio_id", nullable = false)
    private Long anioId;

    @Column(name = "nivel_id")
    private Long nivelId;

    @Column(name = "grado_id", nullable = false)
    private Long gradoId;

    @Column(name = "seccion_id", nullable = false)
    private Long seccionId;

    @Column(name = "campania_id")
    private Long campaniaId;

    @Builder.Default
    @Column(nullable = false, length = 15)
    private String tipo = "NUEVA";

    @Builder.Default
    @Column(nullable = false, length = 15)
    private String estado = "PRE_MATRICULA";

    @Column(name = "matricula_anterior_id")
    private Long matriculaAnteriorId;

    @Column(name = "vence_en")
    private LocalDateTime venceEn;

    @Column(length = 500)
    private String observaciones;

    @Column(name = "creado_por")
    private Long creadoPor;

    @Builder.Default
    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn = LocalDateTime.now();

    @Column(name = "confirmada_en")
    private LocalDateTime confirmadaEn;

    @Column(name = "cerrada_en")
    private LocalDateTime cerradaEn;

    @Column(name = "motivo_cierre", length = 300)
    private String motivoCierre;

    @Builder.Default
    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime actualizadoEn = LocalDateTime.now();

    @Version
    @Column(nullable = false)
    private Long version;
}
