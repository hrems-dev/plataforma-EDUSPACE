package pe.edu.upeu.eduspace.pago.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.pago")
public class PagoProperties {

    private BigDecimal moraPorcentajeMensual = new BigDecimal("2.00");
    private String moraBase = "SALDO";
    private int diasAvisoPrevio = 3;
    private int diasVigenciaPrematricula = 7;
    private int margenGraciaDias = 0;
}

