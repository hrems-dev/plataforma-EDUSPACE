package pe.edu.eduspace.notificaciones.config;

import feign.RequestInterceptor;
import org.slf4j.MDC;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pe.edu.eduspace.notificaciones.client.AlumnosClient;
import pe.edu.eduspace.notificaciones.filter.CorrelationIdFilter;

@Configuration
@EnableFeignClients(basePackageClasses = AlumnosClient.class)
public class FeignConfig {

    /** Propaga {@code X-Trace-ID} en cada llamada a otro servicio. */
    @Bean
    RequestInterceptor traceIdInterceptor(@Value("${eduspace.service-token:}") String serviceToken) {
        return template -> {
            if (serviceToken != null && !serviceToken.isBlank() && template.path().contains("/internal/")) {
                template.header("X-Service-Token", serviceToken);
            }
            String traceId = MDC.get(CorrelationIdFilter.MDC_KEY);
            if (traceId != null) {
                template.header(CorrelationIdFilter.HEADER, traceId);
            }
        };
    }
}
