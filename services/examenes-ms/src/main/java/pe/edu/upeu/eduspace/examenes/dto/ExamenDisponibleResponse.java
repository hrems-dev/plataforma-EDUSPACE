package pe.edu.upeu.eduspace.examenes.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class ExamenDisponibleResponse {
    private Long examenId;
    private Long cursoId;
    private String titulo;
    private String instrucciones;
    private LocalDateTime fechaInicio;
    private LocalDateTime fechaFin;
    private Short duracionMin;
    private Short intentosMax;
    private short intentosRealizados;
    private boolean puedeRendir;
    private BigDecimal puntajeTotal;
}

