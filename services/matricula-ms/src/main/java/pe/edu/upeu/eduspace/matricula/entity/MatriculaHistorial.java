package pe.edu.upeu.eduspace.matricula.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "matricula_historial")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MatriculaHistorial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "matricula_id", nullable = false)
    private Long matriculaId;

    @Column(name = "tipo_cambio", nullable = false, length = 20)
    private String tipoCambio;

    @Column(name = "estado_anterior", length = 15)
    private String estadoAnterior;

    @Column(name = "estado_nuevo", length = 15)
    private String estadoNuevo;

    @Column(name = "seccion_anterior_id")
    private Long seccionAnteriorId;

    @Column(name = "seccion_nueva_id")
    private Long seccionNuevaId;

    @Column(length = 300)
    private String motivo;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Builder.Default
    @Column(nullable = false)
    private LocalDateTime fecha = LocalDateTime.now();
}
