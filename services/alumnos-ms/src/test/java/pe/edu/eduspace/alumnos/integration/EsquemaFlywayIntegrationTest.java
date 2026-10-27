package pe.edu.eduspace.alumnos.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import pe.edu.eduspace.alumnos.storage.AlmacenamientoArchivos;

/** Si el contexto arranca, Hibernate (ddl-auto=validate) confirmó que las 6 entidades calzan con el SQL. */
class EsquemaFlywayIntegrationTest extends PostgresIntegrationTestBase {

    @MockitoBean KafkaTemplate<String, String> kafkaTemplate;
    @MockitoBean AlmacenamientoArchivos almacenamiento;
    @Autowired JdbcTemplate jdbc;

    @Test
    void flywayCreaLas6TablasMasOutbox() {
        List<String> tablas = jdbc.queryForList("""
                select table_name from information_schema.tables
                where table_schema = 'public' and table_name <> 'flyway_schema_history' order by 1
                """, String.class);
        assertThat(tablas).containsExactlyInAnyOrder("alumnos", "apoderados", "alumno_apoderado", "alumno_documentos",
                "alumno_salud", "alumno_estado_historial", "outbox_eventos");
    }

    @Test
    void laBaseImpideDosPrincipalesYDosResponsablesDePago() {
        jdbc.update("insert into alumnos (codigo, numero_documento, nombres, apellido_paterno, fecha_nacimiento, sexo) values ('T-1','11111111','A','B','2015-01-01','M')");
        jdbc.update("insert into apoderados (numero_documento, nombres, apellidos) values ('22222222','P','Q'), ('33333333','R','S')");
        try {
            Long alumno = jdbc.queryForObject("select id from alumnos where codigo = 'T-1'", Long.class);
            List<Long> apoderados = jdbc.queryForList("select id from apoderados where numero_documento in ('22222222','33333333') order by id", Long.class);
            jdbc.update("insert into alumno_apoderado (alumno_id, apoderado_id, parentesco, es_principal, es_responsable_pago) values (?,?,'PADRE',true,true)", alumno, apoderados.get(0));
            org.junit.jupiter.api.Assertions.assertThrows(DuplicateKeyException.class, () ->
                    jdbc.update("insert into alumno_apoderado (alumno_id, apoderado_id, parentesco, es_principal) values (?,?,'MADRE',true)", alumno, apoderados.get(1)));
            org.junit.jupiter.api.Assertions.assertThrows(DuplicateKeyException.class, () ->
                    jdbc.update("insert into alumno_apoderado (alumno_id, apoderado_id, parentesco, es_responsable_pago) values (?,?,'MADRE',true)", alumno, apoderados.get(1)));
        } finally {
            jdbc.update("delete from alumnos where codigo = 'T-1'");
            jdbc.update("delete from apoderados where numero_documento in ('22222222','33333333')");
        }
    }
}
