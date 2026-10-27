package pe.edu.upeu.eduspace.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class EduspaceGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(EduspaceGatewayApplication.class, args);
    }
}

