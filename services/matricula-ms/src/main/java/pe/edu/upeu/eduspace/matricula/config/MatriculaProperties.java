package pe.edu.upeu.eduspace.matricula.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.matricula")
public class MatriculaProperties {

    /** Días que una PRE_MATRICULA espera el pago antes de anularse. */
    private int diasVigenciaPrematricula = 7;
    private int maxIntentosOutbox = 10;
}
