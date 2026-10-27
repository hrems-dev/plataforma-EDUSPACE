package pe.edu.upeu.eduspace.asistencia;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;
import pe.edu.upeu.eduspace.asistencia.config.AsistenciaProperties;

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
@EnableCaching
@EnableScheduling
@EnableConfigurationProperties(AsistenciaProperties.class)
public class AsistenciaApplication {

    public static void main(String[] args) {
        SpringApplication.run(AsistenciaApplication.class, args);
    }
}
