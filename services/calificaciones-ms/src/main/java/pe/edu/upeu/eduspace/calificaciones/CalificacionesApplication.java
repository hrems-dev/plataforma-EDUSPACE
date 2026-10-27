package pe.edu.upeu.eduspace.calificaciones;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;
import pe.edu.upeu.eduspace.calificaciones.config.CalificacionesProperties;

@SpringBootApplication
@EnableDiscoveryClient
@EnableCaching
@EnableScheduling
@EnableConfigurationProperties(CalificacionesProperties.class)
public class CalificacionesApplication {

    public static void main(String[] args) {
        SpringApplication.run(CalificacionesApplication.class, args);
    }
}

