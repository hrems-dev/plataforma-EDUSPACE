package pe.edu.upeu.eduspace.pago.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class MorososResponse {
    private Long seccionId;
    private Long gradoId;
    private Long totalAlumnosMorosos;
    private BigDecimal totalDeudaAcumulada;
    private List<DetalleMoroso> listaMorosos;

    @Data
    @Builder
    public static class DetalleMoroso {
        private Long alumnoId;
        private String alumnoNombre;
        private Long matriculaId;
        private String responsableNombre;
        private BigDecimal saldoTotalVencido;
        private int cuotasVencidas;
    }
}

