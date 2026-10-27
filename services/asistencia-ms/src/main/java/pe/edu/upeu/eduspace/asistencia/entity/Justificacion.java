package pe.edu.upeu.eduspace.asistencia.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "justificaciones")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Justificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "matricula_id", nullable = false)
    private Long matriculaId;

    @Column(name = "alumno_id", nullable = false)
    private Long alumnoId;

    @Column(name = "fecha_desde", nullable = false)
    private LocalDate fechaDesde;

    @Column(name = "fecha_hasta", nullable = false)
    private LocalDate fechaHasta;

    @Column(nullable = false, length = 10)
    private String tipo;

    @Column(nullable = false, length = 500)
    private String motivo;

    @Column(name = "ruta_archivo", length = 400)
    private String rutaArchivo;

    @Builder.Default
    @Column(nullable = false, length = 10)
    private String estado = "PENDIENTE";

    @Column(name = "solicitado_por", nullable = false)
    private Long solicitadoPor;

    @Column(name = "resuelto_por")
    private Long resueltoPor;

    @Column(name = "resuelto_en")
    private LocalDateTime resueltoEn;

    @Column(name = "comentario_resolucion", length = 300)
    private String comentarioResolucion;

    @Builder.Default
    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn = LocalDateTime.now();
}
