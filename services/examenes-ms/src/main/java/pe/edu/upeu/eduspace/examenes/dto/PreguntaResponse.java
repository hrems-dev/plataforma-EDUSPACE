package pe.edu.upeu.eduspace.examenes.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class PreguntaResponse {
    private Long id;
    private Long cursoId;
    private Long docenteId;
    private String tema;
    private String tipo;
    private String enunciado;
    private BigDecimal puntaje;
    private Boolean activa;
    private LocalDateTime creadaEn;
    private List<OpcionResponse> opciones;
}

