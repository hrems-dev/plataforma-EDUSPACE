package pe.edu.upeu.eduspace.calificaciones.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotaResponse {

    private Long id;
    private Long matriculaId;
    private Long alumnoId;
    private Long seccionId;
    private Long cursoId;
    private Long periodoId;
    private Long criterioId;
    private String criterioNombre;
    private BigDecimal valor;
    private String origen;
    private Long origenId;
    private Long registradoPor;
    private LocalDateTime registradoEn;
    private LocalDateTime actualizadoEn;
}

