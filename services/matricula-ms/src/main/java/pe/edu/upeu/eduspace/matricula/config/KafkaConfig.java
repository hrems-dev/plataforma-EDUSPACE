package pe.edu.upeu.eduspace.matricula.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@EnableKafka
public class KafkaConfig {

    public static final String TOPIC_MATRICULA_CREADA = "matricula.creada";
    public static final String TOPIC_MATRICULA_CONFIRMADA = "matricula.confirmada";
    public static final String TOPIC_MATRICULA_TRASLADADA = "matricula.trasladada";
    public static final String TOPIC_MATRICULA_RETIRADA = "matricula.retirada";
    public static final String TOPIC_MATRICULA_ANULADA = "matricula.anulada";

    public static final String TOPIC_PAGO_CONFIRMADO = "pago.confirmado";
    public static final String TOPIC_PAGO_ANULADO = "pago.anulado";

    @Bean
    NewTopic matriculaCreada() { return TopicBuilder.name(TOPIC_MATRICULA_CREADA).partitions(3).replicas(1).build(); }

    @Bean
    NewTopic matriculaConfirmada() { return TopicBuilder.name(TOPIC_MATRICULA_CONFIRMADA).partitions(3).replicas(1).build(); }

    @Bean
    NewTopic matriculaTrasladada() { return TopicBuilder.name(TOPIC_MATRICULA_TRASLADADA).partitions(3).replicas(1).build(); }

    @Bean
    NewTopic matriculaRetirada() { return TopicBuilder.name(TOPIC_MATRICULA_RETIRADA).partitions(3).replicas(1).build(); }

    @Bean
    NewTopic matriculaAnulada() { return TopicBuilder.name(TOPIC_MATRICULA_ANULADA).partitions(3).replicas(1).build(); }
}
