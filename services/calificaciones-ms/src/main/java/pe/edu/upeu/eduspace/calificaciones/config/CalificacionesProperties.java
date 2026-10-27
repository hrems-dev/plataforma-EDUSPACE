package pe.edu.upeu.eduspace.calificaciones.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.calificaciones")
public class CalificacionesProperties {

    private BigDecimal notaMaxima = new BigDecimal("20.00");
    private BigDecimal notaMinimaAprobatoria = new BigDecimal("11.00");
    private BigDecimal notaMinimaRecuperacion = new BigDecimal("8.00");
}

