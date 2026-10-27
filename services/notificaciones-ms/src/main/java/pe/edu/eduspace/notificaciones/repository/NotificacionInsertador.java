package pe.edu.eduspace.notificaciones.repository;

import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import pe.edu.eduspace.notificaciones.entity.Notificacion;

/**
 * Inserta una notificación solo si no existe ya la misma (usuario, tipo, canal, evento): Kafka entrega al menos una
 * vez y la restricción única de la base es la red de seguridad. {@code ON CONFLICT DO NOTHING} evita que la violación
 * marque la transacción como revertida.
 */
@Repository
public class NotificacionInsertador {

    private static final String SQL = """
            INSERT INTO notificaciones (usuario_id, tipo, canal, titulo, mensaje, datos_json, evento_origen, evento_clave,
                                        estado, creada_en, enviada_en)
            VALUES (:usuario, :tipo, :canal, :titulo, :mensaje, CAST(:datos AS jsonb), :origen, :clave, :estado, now(), :enviada)
            ON CONFLICT DO NOTHING
            RETURNING id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public NotificacionInsertador(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** @return el id creado, o vacío si ya existía una igual. */
    public Optional<Long> insertar(Notificacion n) {
        var params = new MapSqlParameterSource()
                .addValue("usuario", n.getUsuarioId())
                .addValue("tipo", n.getTipo())
                .addValue("canal", n.getCanal().name())
                .addValue("titulo", n.getTitulo())
                .addValue("mensaje", n.getMensaje())
                .addValue("datos", n.getDatosJson())
                .addValue("origen", n.getEventoOrigen())
                .addValue("clave", n.getEventoClave())
                .addValue("estado", n.getEstado().name())
                .addValue("enviada", n.getEnviadaEn() == null ? null : java.sql.Timestamp.valueOf((LocalDateTime) n.getEnviadaEn()));
        return jdbc.query(SQL, params, rs -> rs.next() ? Optional.of(rs.getLong(1)) : Optional.empty());
    }
}
