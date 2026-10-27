package pe.edu.upeu.eduspace.matricula.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "cupos_seccion")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CupoSeccion {

    @Id
    @Column(name = "seccion_id")
    private Long seccionId;

    @Column(name = "anio_id", nullable = false)
    private Long anioId;

    @Column(name = "nivel_id")
    private Long nivelId;

    @Column(name = "grado_id", nullable = false)
    private Long gradoId;

    @Column(name = "seccion_nombre", nullable = false, length = 10)
    private String seccionNombre;

    @Column(nullable = false)
    private Short capacidad;

    @Builder.Default
    @Column(nullable = false)
    private Short ocupados = 0;

    @Builder.Default
    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime actualizadoEn = LocalDateTime.now();
}
