package pe.edu.upeu.eduspace.pago.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@EnableKafka
public class KafkaConfig {

    public static final String TOPIC_PAGO_CONFIRMADO = "pago.confirmado";
    public static final String TOPIC_PAGO_POR_VENCER = "pago.por-vencer";
    public static final String TOPIC_PAGO_VENCIDO = "pago.vencido";
    public static final String TOPIC_PAGO_ANULADO = "pago.anulado";

    public static final String TOPIC_MATRICULA_CREADA = "matricula.creada";
    public static final String TOPIC_MATRICULA_CONFIRMADA = "matricula.confirmada";
    public static final String TOPIC_MATRICULA_TRASLADADA = "matricula.trasladada";
    public static final String TOPIC_MATRICULA_RETIRADA = "matricula.retirada";
    public static final String TOPIC_MATRICULA_ANULADA = "matricula.anulada";

    @Bean
    public NewTopic pagoConfirmadoTopic() {
        return TopicBuilder.name(TOPIC_PAGO_CONFIRMADO).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic pagoPorVencerTopic() {
        return TopicBuilder.name(TOPIC_PAGO_POR_VENCER).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic pagoVencidoTopic() {
        return TopicBuilder.name(TOPIC_PAGO_VENCIDO).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic pagoAnuladoTopic() {
        return TopicBuilder.name(TOPIC_PAGO_ANULADO).partitions(3).replicas(1).build();
    }
}

