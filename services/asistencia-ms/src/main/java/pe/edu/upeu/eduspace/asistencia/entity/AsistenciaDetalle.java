package pe.edu.upeu.eduspace.asistencia.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "asistencia_detalle")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AsistenciaDetalle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sesion_id", nullable = false)
    private SesionAsistencia sesion;

    @Column(name = "matricula_id", nullable = false)
    private Long matriculaId;

    @Column(name = "alumno_id", nullable = false)
    private Long alumnoId;

    @Column(nullable = false, length = 11)
    private String estado;

    @Column(name = "minutos_tarde")
    private Short minutosTarde;

    @Column(length = 300)
    private String observacion;

    @Builder.Default
    @Column(name = "registrado_en", nullable = false)
    private LocalDateTime registradoEn = LocalDateTime.now();

    @Builder.Default
    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime actualizadoEn = LocalDateTime.now();

    @Builder.Default
    @Column(nullable = false)
    private Boolean confirmado = false;

    @Builder.Default
    @Column(name = "falta_notificada", nullable = false)
    private Boolean faltaNotificada = false;
}
