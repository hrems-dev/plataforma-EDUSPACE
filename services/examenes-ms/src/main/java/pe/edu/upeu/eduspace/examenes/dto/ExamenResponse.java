package pe.edu.upeu.eduspace.examenes.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class ExamenResponse {
    private Long id;
    private Long anioId;
    private Long periodoId;
    private Long seccionId;
    private Long cursoId;
    private Long docenteId;
    private String titulo;
    private String instrucciones;
    private LocalDateTime fechaInicio;
    private LocalDateTime fechaFin;
    private Short duracionMin;
    private Short intentosMax;
    private Boolean barajarPreguntas;
    private BigDecimal puntajeTotal;
    private String estado;
    private LocalDateTime creadoEn;
    private LocalDateTime publicadoEn;
}

