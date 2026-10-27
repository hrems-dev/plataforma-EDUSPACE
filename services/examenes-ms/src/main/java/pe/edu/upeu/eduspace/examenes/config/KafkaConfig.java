package pe.edu.upeu.eduspace.examenes.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@EnableKafka
public class KafkaConfig {

    public static final String TOPIC_EXAMEN_PUBLICADO = "examen.publicado";
    public static final String TOPIC_EXAMEN_CALIFICADO = "examen.calificado";

    public static final String TOPIC_MATRICULA_CREADA = "matricula.creada";
    public static final String TOPIC_MATRICULA_CONFIRMADA = "matricula.confirmada";
    public static final String TOPIC_MATRICULA_TRASLADADA = "matricula.trasladada";
    public static final String TOPIC_MATRICULA_RETIRADA = "matricula.retirada";
    public static final String TOPIC_MATRICULA_ANULADA = "matricula.anulada";

    @Bean
    public NewTopic examenPublicadoTopic() {
        return TopicBuilder.name(TOPIC_EXAMEN_PUBLICADO).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic examenCalificadoTopic() {
        return TopicBuilder.name(TOPIC_EXAMEN_CALIFICADO).partitions(3).replicas(1).build();
    }
}

