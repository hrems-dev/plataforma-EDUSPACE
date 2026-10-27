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
public class PromedioResponse {

    private Long id;
    private Long matriculaId;
    private Long alumnoId;
    private String alumnoNombre;
    private Long seccionId;
    private Long cursoId;
    private Long periodoId;
    private BigDecimal promedio;
    private String literal;
    private String estado;
    private LocalDateTime calculadoEn;
}

