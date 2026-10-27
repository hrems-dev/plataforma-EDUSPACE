package pe.edu.upeu.eduspace.examenes.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class IntentoIniciadoResponse {
    private Long intentoId;
    private Long examenId;
    private String tituloExamen;
    private Short numeroIntento;
    private LocalDateTime iniciadoEn;
    private LocalDateTime venceEn;
    private BigDecimal puntajeTotal;
    private List<PreguntaEstudianteDto> preguntas;

    @Data
    @Builder
    public static class PreguntaEstudianteDto {
        private Long preguntaId;
        private Short orden;
        private BigDecimal puntaje;
        private String tipo;
        private String enunciado;
        private List<OpcionEstudianteDto> opciones;
        // Si ya tenía respuesta guardada previamente (por reconexión)
        private List<Long> opcionesRespondidas;
        private String textoRespondido;
    }

    @Data
    @Builder
    public static class OpcionEstudianteDto {
        private Long id;
        private String texto;
        private Short orden;
    }
}

