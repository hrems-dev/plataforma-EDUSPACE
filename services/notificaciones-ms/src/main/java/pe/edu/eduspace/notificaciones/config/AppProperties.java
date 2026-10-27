package pe.edu.eduspace.notificaciones.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Parámetros de negocio de notificaciones-ms (prefijo {@code app}). */
@ConfigurationProperties(prefix = "app")
public record AppProperties(Reintentos reintentos, Contactos contactos, Limpieza limpieza, Mail mail) {

    public AppProperties {
        reintentos = reintentos == null ? new Reintentos(null, null) : reintentos;
        contactos = contactos == null ? new Contactos(null, null) : contactos;
        limpieza = limpieza == null ? new Limpieza(null) : limpieza;
        mail = mail == null ? new Mail(null) : mail;
    }

    /** Intentos máximos por notificación y tamaño del lote de cada ciclo del planificador. */
    public record Reintentos(Integer maxIntentos, Integer lote) {
        public Reintentos {
            maxIntentos = maxIntentos == null ? 3 : maxIntentos;
            lote = lote == null ? 100 : lote;
        }
    }

    /** Caché local de contactos de alumnos-ms (adenda A6). */
    public record Contactos(Duration cacheTtl, Long cacheMaxSize) {
        public Contactos {
            cacheTtl = cacheTtl == null ? Duration.ofMinutes(5) : cacheTtl;
            cacheMaxSize = cacheMaxSize == null ? 5000L : cacheMaxSize;
        }
    }

    /** Días que se conservan los eventos procesados (adenda A4). */
    public record Limpieza(Integer diasEventosProcesados) {
        public Limpieza {
            diasEventosProcesados = diasEventosProcesados == null ? 30 : diasEventosProcesados;
        }
    }

    public record Mail(String from) {
        public Mail {
            from = from == null || from.isBlank() ? "notificaciones@eduspace.pe" : from;
        }
    }
}
