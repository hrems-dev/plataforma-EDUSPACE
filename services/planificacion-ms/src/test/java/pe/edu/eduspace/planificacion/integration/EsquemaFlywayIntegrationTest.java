package pe.edu.eduspace.planificacion.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Si el contexto arranca, Hibernate (ddl-auto=validate) confirmó que las 14 entidades calzan con el SQL. */
class EsquemaFlywayIntegrationTest extends PostgresIntegrationTestBase {

    @MockitoBean KafkaTemplate<String, String> kafkaTemplate;
    @Autowired JdbcTemplate jdbc;

    @Test
    void flywayCreaLas14TablasYLaSemilla() {
        List<String> tablas = jdbc.queryForList("""
                select table_name from information_schema.tables
                where table_schema = 'public' and table_name <> 'flyway_schema_history' order by 1
                """, String.class);
        assertThat(tablas).containsExactlyInAnyOrder("anios_academicos", "periodos", "niveles", "grados", "aulas",
                "secciones", "cursos", "grado_cursos", "docentes", "asignaciones", "bloques_horarios", "horarios",
                "publicaciones_horario", "feriados", "outbox_eventos");
        assertThat(jdbc.queryForObject("select count(*) from niveles", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("select count(*) from grados", Integer.class)).isEqualTo(14);
    }

    @Test
    void laBaseImpideDosAniosActivos() {
        jdbc.update("insert into anios_academicos (nombre, fecha_inicio, fecha_fin, estado) values ('A1','2030-01-01','2030-12-01','ACTIVO')");
        try {
            org.junit.jupiter.api.Assertions.assertThrows(org.springframework.dao.DuplicateKeyException.class, () ->
                    jdbc.update("insert into anios_academicos (nombre, fecha_inicio, fecha_fin, estado) values ('A2','2031-01-01','2031-12-01','ACTIVO')"));
        } finally {
            jdbc.update("delete from anios_academicos where nombre in ('A1','A2')");
        }
    }
}
