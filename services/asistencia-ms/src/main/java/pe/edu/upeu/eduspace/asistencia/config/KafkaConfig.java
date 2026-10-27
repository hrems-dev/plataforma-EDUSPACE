package pe.edu.upeu.eduspace.asistencia.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@EnableKafka
public class KafkaConfig {

    public static final String TOPIC_FALTA_REGISTRADA = "asistencia.falta-registrada";
    public static final String TOPIC_JUSTIFICACION_RESUELTA = "justificacion.resuelta";

    public static final String TOPIC_MATRICULA_CREADA = "matricula.creada";
    public static final String TOPIC_MATRICULA_CONFIRMADA = "matricula.confirmada";
    public static final String TOPIC_MATRICULA_TRASLADADA = "matricula.trasladada";
    public static final String TOPIC_MATRICULA_RETIRADA = "matricula.retirada";
    public static final String TOPIC_MATRICULA_ANULADA = "matricula.anulada";

    @Bean
    NewTopic faltaRegistrada() { return TopicBuilder.name(TOPIC_FALTA_REGISTRADA).partitions(3).replicas(1).build(); }

    @Bean
    NewTopic justificacionResuelta() { return TopicBuilder.name(TOPIC_JUSTIFICACION_RESUELTA).partitions(3).replicas(1).build(); }
}
