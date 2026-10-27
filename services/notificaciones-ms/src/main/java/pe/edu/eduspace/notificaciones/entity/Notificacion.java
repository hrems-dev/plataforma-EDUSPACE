package pe.edu.eduspace.notificaciones.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "notificaciones")
@Getter
@Setter
public class Notificacion {

    /** Destinatario "aún sin resolver": la notificación espera a que alumnos-ms vuelva para conocer a quién va. */
    public static final long SIN_DESTINATARIO = 0L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    /** Destinatario (usuario_id en auth-ms); número simple, sin relación. */
    private Long usuarioId;
    private String tipo;
    @Enumerated(EnumType.STRING)
    private Canal canal = Canal.IN_APP;
    private String titulo;
    private String mensaje;
    /** Datos extra del evento en JSON (deep-link del cliente, correo del destinatario, etc.). */
    @JdbcTypeCode(SqlTypes.JSON)
    private String datosJson;
    private String eventoOrigen;
    private String eventoClave;
    @Enumerated(EnumType.STRING)
    private EstadoNotificacion estado = EstadoNotificacion.PENDIENTE;
    private Boolean leida = false;
    private LocalDateTime leidaEn;
    private LocalDateTime creadaEn;
    private LocalDateTime enviadaEn;

    @PrePersist
    void onCreate() {
        if (creadaEn == null) {
            creadaEn = LocalDateTime.now();
        }
    }
}
