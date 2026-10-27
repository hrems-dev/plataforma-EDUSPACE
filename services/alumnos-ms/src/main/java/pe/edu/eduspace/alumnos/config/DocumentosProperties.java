package pe.edu.eduspace.alumnos.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Reglas de subida y descarga de documentos del expediente. */
@ConfigurationProperties(prefix = "app.documentos")
public record DocumentosProperties(Long maxBytes, List<String> tiposPermitidos, Duration vigenciaDescarga) {

    public DocumentosProperties {
        maxBytes = maxBytes == null ? 5L * 1024 * 1024 : maxBytes;
        tiposPermitidos = tiposPermitidos == null || tiposPermitidos.isEmpty()
                ? List.of("application/pdf", "image/jpeg", "image/png") : tiposPermitidos;
        vigenciaDescarga = vigenciaDescarga == null ? Duration.ofMinutes(10) : vigenciaDescarga;
    }
}
