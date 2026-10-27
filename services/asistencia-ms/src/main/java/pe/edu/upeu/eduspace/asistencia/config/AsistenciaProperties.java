package pe.edu.upeu.eduspace.asistencia.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.asistencia")
public class AsistenciaProperties {

    private String estadoPorDefecto = "PRESENTE";
    private boolean marcarFaltaSinRegistro = false;
    private int limiteInasistenciaPorcentaje = 30;
    private int archivoMaxMb = 5;
    private List<String> tiposArchivo = List.of("application/pdf", "image/jpeg", "image/png");
}
