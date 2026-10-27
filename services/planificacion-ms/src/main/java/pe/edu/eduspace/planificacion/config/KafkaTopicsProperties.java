package pe.edu.eduspace.planificacion.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import pe.edu.eduspace.planificacion.event.AnioActivadoEvent;
import pe.edu.eduspace.planificacion.event.HorarioPublicadoEvent;
import pe.edu.eduspace.planificacion.event.PeriodoCambiadoEvent;

/** Nombres de tópicos desde {@code app.kafka.topic.*}. */
@ConfigurationProperties(prefix = "app.kafka.topic")
public record KafkaTopicsProperties(String horarioPublicado, String anioActivado, String periodoCambiado) {

    public KafkaTopicsProperties {
        horarioPublicado = horarioPublicado == null ? HorarioPublicadoEvent.TYPE : horarioPublicado;
        anioActivado = anioActivado == null ? AnioActivadoEvent.TYPE : anioActivado;
        periodoCambiado = periodoCambiado == null ? PeriodoCambiadoEvent.TYPE : periodoCambiado;
    }

    public String topicFor(String eventType) {
        return switch (eventType) {
            case HorarioPublicadoEvent.TYPE -> horarioPublicado;
            case AnioActivadoEvent.TYPE -> anioActivado;
            case PeriodoCambiadoEvent.TYPE -> periodoCambiado;
            default -> eventType;
        };
    }
}
