package pe.edu.upeu.eduspace.asistencia.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "asistencia_auditoria")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AsistenciaAuditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "detalle_id", nullable = false)
    private Long detalleId;

    @Column(name = "estado_anterior", length = 11)
    private String estadoAnterior;

    @Column(name = "estado_nuevo", nullable = false, length = 11)
    private String estadoNuevo;

    @Column(length = 300)
    private String motivo;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Builder.Default
    @Column(name = "cambiado_en", nullable = false)
    private LocalDateTime cambiadoEn = LocalDateTime.now();
}
