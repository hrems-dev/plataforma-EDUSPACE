package pe.edu.eduspace.notificaciones.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import pe.edu.eduspace.notificaciones.service.EmailSender;

/** Si el contexto arranca, Hibernate (ddl-auto=validate) confirmó que las entidades calzan con V1, V2 y V3. */
class EsquemaFlywayIntegrationTest extends PostgresIntegrationTestBase {

    @MockitoBean EmailSender emailSender;
    @Autowired JdbcTemplate jdbc;

    @Test
    void flywayCreaLas7TablasYLasPlantillas() {
        List<String> tablas = jdbc.queryForList("""
                select table_name from information_schema.tables
                where table_schema = 'public' and table_name <> 'flyway_schema_history' order by 1
                """, String.class);
        assertThat(tablas).containsExactlyInAnyOrder("plantillas_notificacion", "notificaciones",
                "preferencias_notificacion", "dispositivos_push", "intentos_envio", "shedlock", "eventos_procesados");
        // 8 (V1) + 5 (V2) + 6 (V3) + 1 (V4)
        assertThat(jdbc.queryForObject("select count(*) from plantillas_notificacion", Integer.class)).isEqualTo(20);
        assertThat(jdbc.queryForObject("select count(*) from plantillas_notificacion where codigo = 'HORARIO_PUBLICADO'", Integer.class)).isEqualTo(1);
    }

    @Test
    void laBaseImpideLaMismaNotificacionDosVeces() {
        String sql = "insert into notificaciones (usuario_id, tipo, canal, titulo, mensaje, evento_origen, evento_clave) values (?,?,?,?,?,?,?)";
        jdbc.update(sql, 9001L, "PAGO_CONFIRMADO", "IN_APP", "t", "m", "pago.confirmado", "77");
        try {
            org.junit.jupiter.api.Assertions.assertThrows(DuplicateKeyException.class,
                    () -> jdbc.update(sql, 9001L, "PAGO_CONFIRMADO", "IN_APP", "t", "m", "pago.confirmado", "77"));
            // otro canal o clave distinta sí se permiten
            jdbc.update(sql, 9001L, "PAGO_CONFIRMADO", "EMAIL", "t", "m", "pago.confirmado", "77");
            jdbc.update(sql, 9001L, "PAGO_CONFIRMADO", "IN_APP", "t", "m", "pago.confirmado", "78");
        } finally {
            jdbc.update("delete from notificaciones where usuario_id = 9001");
        }
    }
}
