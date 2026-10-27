package pe.edu.eduspace.planificacion.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/** Declara los 3 tópicos que produce este servicio (3 particiones, 1 réplica). */
@Configuration
@EnableConfigurationProperties(KafkaTopicsProperties.class)
public class KafkaTopicsConfig {

    @Bean
    NewTopic horarioPublicadoTopic(KafkaTopicsProperties props) {
        return TopicBuilder.name(props.horarioPublicado()).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic anioActivadoTopic(KafkaTopicsProperties props) {
        return TopicBuilder.name(props.anioActivado()).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic periodoCambiadoTopic(KafkaTopicsProperties props) {
        return TopicBuilder.name(props.periodoCambiado()).partitions(3).replicas(1).build();
    }
}
