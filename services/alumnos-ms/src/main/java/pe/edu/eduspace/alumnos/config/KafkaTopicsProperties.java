package pe.edu.eduspace.alumnos.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import pe.edu.eduspace.alumnos.event.AlumnoEstadoCambiadoEvent;
import pe.edu.eduspace.alumnos.event.AlumnoRegistradoEvent;

/** Nombres de tópicos desde {@code app.kafka.topic.*}. */
@ConfigurationProperties(prefix = "app.kafka.topic")
public record KafkaTopicsProperties(String alumnoRegistrado, String alumnoEstadoCambiado) {

    public KafkaTopicsProperties {
        alumnoRegistrado = alumnoRegistrado == null ? AlumnoRegistradoEvent.TYPE : alumnoRegistrado;
        alumnoEstadoCambiado = alumnoEstadoCambiado == null ? AlumnoEstadoCambiadoEvent.TYPE : alumnoEstadoCambiado;
    }

    public String topicFor(String eventType) {
        return switch (eventType) {
            case AlumnoRegistradoEvent.TYPE -> alumnoRegistrado;
            case AlumnoEstadoCambiadoEvent.TYPE -> alumnoEstadoCambiado;
            default -> eventType;
        };
    }
}
