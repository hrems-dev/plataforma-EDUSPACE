package pe.edu.upeu.eduspace.examenes.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.examenes")
public class ExamenProperties {

    private int margenGraciaSegundos = 60;
    private int maxPreguntasPorExamen = 50;
    private boolean calificacionMultipleParcial = true;
}

