package pe.edu.upeu.eduspace.matricula.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Trámite de matrícula iniciado desde el sitio público (ver V3). */
@Entity
@Table(name = "matriculas_publicas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MatriculaPublica {

    @Id
    @Column(name = "matricula_id")
    private Long matriculaId;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "apoderado_id", nullable = false)
    private Long apoderadoId;

    @Column(name = "apoderado_usuario_id")
    private Long apoderadoUsuarioId;

    @Column(name = "apoderado_nombre", nullable = false, length = 200)
    private String apoderadoNombre;

    @Column(name = "apoderado_nombres", length = 100)
    private String apoderadoNombres;

    @Column(name = "apoderado_apellidos", length = 150)
    private String apoderadoApellidos;

    @Column(name = "apoderado_documento", nullable = false, length = 20)
    private String apoderadoDocumento;

    @Column(name = "apoderado_email", length = 150)
    private String apoderadoEmail;

    @Column(name = "alumno_nombres", nullable = false, length = 100)
    private String alumnoNombres;

    @Column(name = "alumno_apellidos", nullable = false, length = 160)
    private String alumnoApellidos;

    @Column(name = "alumno_documento", nullable = false, length = 20)
    private String alumnoDocumento;

    @Builder.Default
    @Column(nullable = false, length = 12)
    private String estado = "INICIADA";

    @Column(name = "pago_id")
    private Long pagoId;

    @Column(length = 30)
    private String comprobante;

    @Column(name = "monto_matricula", precision = 10, scale = 2)
    private BigDecimal montoMatricula;

    @Column(name = "alumno_usuario", length = 80)
    private String alumnoUsuario;

    @Column(name = "apoderado_usuario", length = 80)
    private String apoderadoUsuario;

    @Column(name = "correo_estado", length = 12)
    private String correoEstado;

    @Builder.Default
    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn = LocalDateTime.now();

    @Column(name = "pagada_en")
    private LocalDateTime pagadaEn;
}
