package pe.edu.upeu.eduspace.examenes.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ExamenDetalleResponse {
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
    private List<PreguntaItemDetalle> preguntas;

    @Data
    @Builder
    public static class PreguntaItemDetalle {
        private Long preguntaId;
        private Short orden;
        private BigDecimal puntaje;
        private String tema;
        private String tipo;
        private String enunciado;
        private List<OpcionResponse> opciones;
    }
}

