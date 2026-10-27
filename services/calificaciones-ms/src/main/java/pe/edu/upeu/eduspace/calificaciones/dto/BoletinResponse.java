package pe.edu.upeu.eduspace.calificaciones.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BoletinResponse {

    private Long id;
    private Long matriculaId;
    private Long alumnoId;
    private Long periodoId;
    private Integer version;
    private String estado;
    private String rutaPdf;
    private String urlDescarga;
    private Long generadoPor;
    private LocalDateTime generadoEn;
}

