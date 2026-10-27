package pe.edu.upeu.eduspace.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;

@SpringBootApplication
@EnableConfigServer
public class EduspaceConfigApplication {

    public static void main(String[] args) {
        SpringApplication.run(EduspaceConfigApplication.class, args);
    }
}

