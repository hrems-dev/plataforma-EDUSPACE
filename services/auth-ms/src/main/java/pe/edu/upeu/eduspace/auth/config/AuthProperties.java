package pe.edu.upeu.eduspace.auth.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {

    private String issuer = "http://localhost:8090";
    private int accessTokenMinutos = 30;
    private int refreshTokenDias = 7;
    private int maxIntentosFallidos = 5;
    private int bloqueoMinutos = 15;
    private int claveMinima = 8;
    private Jwt jwt = new Jwt();

    @Getter
    @Setter
    public static class Jwt {
        private String kid = "eduspace-key-1";
        private String privateKeyPath = "";
        private String publicKeyPath = "";
        private String publicKeyAnteriorPath = "";
        private String kidAnterior = "eduspace-key-0";
        private boolean generarClaveSiFalta = false;
    }
}
