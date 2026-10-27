package pe.edu.upeu.eduspace.tareas.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.tareas")
public class TareasProperties {

    private int archivoMaxMb = 10;
    private int entregaMaxMb = 25;
    private int maxArchivosEntrega = 5;
    private List<String> tiposArchivo = List.of("application/pdf", "image/jpeg", "image/png", "application/zip", "text/plain");
}
