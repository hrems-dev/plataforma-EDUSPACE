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
    /** Matrícula en línea (sitio público). */
    private String colegio = "Colegio Horizonte del Valle";
    /** Dirección del portal que se menciona en el correo de confirmación. */
    private String portalUrl = "http://localhost:4200";
    /** Días que la reserva de una matrícula en línea espera el pago antes de liberar el cupo. */
    private int diasReservaEnLinea = 1;
}
