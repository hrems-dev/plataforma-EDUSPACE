package pe.edu.upeu.eduspace.asistencia.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "sesiones_asistencia")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SesionAsistencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "anio_id", nullable = false)
    private Long anioId;

    @Column(name = "periodo_id")
    private Long periodoId;

    @Column(name = "seccion_id", nullable = false)
    private Long seccionId;

    @Column(nullable = false)
    private LocalDate fecha;

    @Builder.Default
    @Column(nullable = false, length = 6)
    private String tipo = "DIA";

    @Column(name = "curso_id")
    private Long cursoId;

    @Column(name = "bloque_id")
    private Long bloqueId;

    @Column(name = "docente_id", nullable = false)
    private Long docenteId;

    @Builder.Default
    @Column(nullable = false, length = 8)
    private String estado = "ABIERTA";

    @Column(name = "abierta_por")
    private Long abiertaPor;

    @Builder.Default
    @Column(name = "creada_en", nullable = false)
    private LocalDateTime creadaEn = LocalDateTime.now();

    @Column(name = "cerrada_en")
    private LocalDateTime cerradaEn;

    @Column(name = "cerrada_por")
    private Long cerradaPor;
}
