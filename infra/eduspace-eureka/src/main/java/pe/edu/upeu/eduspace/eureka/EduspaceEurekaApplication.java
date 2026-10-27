package pe.edu.upeu.eduspace.eureka;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

@SpringBootApplication
@EnableEurekaServer
public class EduspaceEurekaApplication {

    public static void main(String[] args) {
        SpringApplication.run(EduspaceEurekaApplication.class, args);
    }
}

