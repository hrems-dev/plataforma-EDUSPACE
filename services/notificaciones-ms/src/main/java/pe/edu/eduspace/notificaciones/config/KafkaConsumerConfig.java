package pe.edu.eduspace.notificaciones.config;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import pe.edu.eduspace.notificaciones.event.Topicos;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Consumidor con {@code group-id = notificaciones-ms}. El valor llega como texto JSON y cada consumidor lo
 * convierte a su propia copia del contrato (sin cabeceras de tipo). Un fallo de procesamiento se reintenta un
 * número acotado de veces; si persiste, el mensaje se envía a {@code <tópico>.DLT} (dead-letter) para revisarlo
 * y reprocesarlo, y se avanza para no bloquear la partición.
 */
@Configuration
@EnableKafka
public class KafkaConsumerConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerConfig.class);

    @Bean
    ConsumerFactory<String, String> eventConsumerFactory(KafkaProperties kafkaProperties) {
        Map<String, Object> props = kafkaProperties.buildConsumerProperties();
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "notificaciones-ms");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, StringDeserializer.class);
        props.putIfAbsent(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new DefaultKafkaConsumerFactory<>(props);
    }

    private static final String SUFIJO_DLT = ".DLT";

    @Bean
    KafkaTemplate<String, String> dltKafkaTemplate(KafkaProperties kafkaProperties) {
        Map<String, Object> props = kafkaProperties.buildProducerProperties();
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
    }

    /** Un {@code .DLT} por cada tópico consumido, para que existan antes del primer fallo. */
    @Bean
    KafkaAdmin.NewTopics deadLetterTopics() {
        NewTopic[] topics = Arrays.stream(Topicos.class.getDeclaredFields())
                .filter(f -> f.getType() == String.class && java.lang.reflect.Modifier.isPublic(f.getModifiers()))
                .map(this::valor)
                .map(t -> new NewTopic(t + SUFIJO_DLT, 1, (short) 1))
                .toArray(NewTopic[]::new);
        return new KafkaAdmin.NewTopics(topics);
    }

    private String valor(Field f) {
        try {
            return (String) f.get(null);
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> eventConsumerFactory,
            KafkaTemplate<String, String> dltKafkaTemplate,
            @Value("${spring.kafka.listener.auto-startup:true}") boolean autoStartup) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
        factory.setAutoStartup(autoStartup);
        factory.setConsumerFactory(eventConsumerFactory);
        // los tópicos los crea cada productor: que falten al arrancar no debe tumbar este servicio
        factory.getContainerProperties().setMissingTopicsFatal(false);
        var recoverer = new DeadLetterPublishingRecoverer(dltKafkaTemplate,
                (record, ex) -> new TopicPartition(record.topic() + SUFIJO_DLT, -1));
        var handler = new DefaultErrorHandler((record, ex) -> {
            log.error("component=NotificacionEventConsumer eventType={} key={} status=DLT tras reintentos cause={}",
                    record.topic(), record.key(), ex.getMessage());
            recoverer.accept(record, ex);
        }, new FixedBackOff(1000L, 2L));
        factory.setCommonErrorHandler(handler);
        return factory;
    }
}
