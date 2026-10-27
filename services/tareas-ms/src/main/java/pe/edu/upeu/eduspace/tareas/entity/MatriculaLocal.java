package pe.edu.upeu.eduspace.tareas.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Réplica local de la matrícula, mantenida por eventos matricula.*. */
@Entity
@Table(name = "matriculas_local")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MatriculaLocal {

    @Id
    @Column(name = "matricula_id")
    private Long matriculaId;

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

    @Column(nullable = false, length = 15)
    private String estado;

    @Builder.Default
    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime actualizadoEn = LocalDateTime.now();
}
