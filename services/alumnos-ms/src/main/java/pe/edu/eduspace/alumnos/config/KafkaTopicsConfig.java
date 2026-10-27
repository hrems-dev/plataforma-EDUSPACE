package pe.edu.eduspace.alumnos.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/** Declara los 2 tópicos que produce este servicio (3 particiones, 1 réplica). */
@Configuration
@EnableConfigurationProperties({KafkaTopicsProperties.class, ExpedienteProperties.class, DocumentosProperties.class,
        StorageProperties.class})
public class KafkaTopicsConfig {

    @Bean
    NewTopic alumnoRegistradoTopic(KafkaTopicsProperties props) {
        return TopicBuilder.name(props.alumnoRegistrado()).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic alumnoEstadoCambiadoTopic(KafkaTopicsProperties props) {
        return TopicBuilder.name(props.alumnoEstadoCambiado()).partitions(3).replicas(1).build();
    }
}
