package pe.edu.eduspace.alumnos.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import pe.edu.eduspace.alumnos.entity.TipoDocumento;

/** Documentos que el expediente debe tener verificados para considerarse completo (configurable). */
@ConfigurationProperties(prefix = "app.expediente")
public record ExpedienteProperties(List<TipoDocumento> requeridos) {

    public ExpedienteProperties {
        requeridos = requeridos == null || requeridos.isEmpty()
                ? List.of(TipoDocumento.PARTIDA_NACIMIENTO, TipoDocumento.DNI, TipoDocumento.FICHA_SIAGIE)
                : requeridos;
    }
}
