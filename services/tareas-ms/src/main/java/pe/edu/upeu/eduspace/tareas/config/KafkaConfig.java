package pe.edu.upeu.eduspace.tareas.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@EnableKafka
public class KafkaConfig {

    public static final String TOPIC_TAREA_PUBLICADA = "tarea.publicada";
    public static final String TOPIC_TAREA_ENTREGADA = "tarea.entregada";
    public static final String TOPIC_TAREA_CALIFICADA = "tarea.calificada";
    public static final String TOPIC_TAREA_RECORDATORIO = "tarea.recordatorio";

    public static final String TOPIC_MATRICULA_CREADA = "matricula.creada";
    public static final String TOPIC_MATRICULA_CONFIRMADA = "matricula.confirmada";
    public static final String TOPIC_MATRICULA_TRASLADADA = "matricula.trasladada";
    public static final String TOPIC_MATRICULA_RETIRADA = "matricula.retirada";
    public static final String TOPIC_MATRICULA_ANULADA = "matricula.anulada";

    @Bean
    NewTopic tareaPublicada() { return TopicBuilder.name(TOPIC_TAREA_PUBLICADA).partitions(3).replicas(1).build(); }

    @Bean
    NewTopic tareaEntregada() { return TopicBuilder.name(TOPIC_TAREA_ENTREGADA).partitions(3).replicas(1).build(); }

    @Bean
    NewTopic tareaCalificada() { return TopicBuilder.name(TOPIC_TAREA_CALIFICADA).partitions(3).replicas(1).build(); }

    @Bean
    NewTopic tareaRecordatorio() { return TopicBuilder.name(TOPIC_TAREA_RECORDATORIO).partitions(3).replicas(1).build(); }
}
