package pe.edu.upeu.eduspace.examenes.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class IntentoResultadoResponse {
    private Long intentoId;
    private Long examenId;
    private String tituloExamen;
    private Short numeroIntento;
    private String estado;
    private BigDecimal puntajeObtenido;
    private BigDecimal puntajeTotal;
    private BigDecimal nota; // Escala 0-20
    private LocalDateTime iniciadoEn;
    private LocalDateTime enviadoEn;
    private LocalDateTime calificadoEn;
    private boolean requiereRevisionAbiertas;
}

