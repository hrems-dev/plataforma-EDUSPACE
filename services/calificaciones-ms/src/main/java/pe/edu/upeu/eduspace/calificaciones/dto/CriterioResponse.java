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
public class CriterioResponse {

    private Long id;
    private Long anioId;
    private Long periodoId;
    private Long seccionId;
    private Long cursoId;
    private String nombre;
    private BigDecimal peso;
    private String origenTipo;
    private Long creadoPor;
    private LocalDateTime creadoEn;
}

