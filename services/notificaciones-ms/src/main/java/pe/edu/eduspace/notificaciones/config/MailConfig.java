package pe.edu.eduspace.notificaciones.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Habilita las propiedades de la aplicación; el servidor SMTP lo configura {@code spring.mail.*} (Mailpit en DEV). */
@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class MailConfig {
}
