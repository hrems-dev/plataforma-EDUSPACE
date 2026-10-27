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
public class CierrePeriodoResponse {

    private Long id;
    private Long seccionId;
    private Long periodoId;
    private String estado;
    private Long cerradoPor;
    private LocalDateTime cerradoEn;
    private Long reabiertoPor;
    private LocalDateTime reabiertoEn;
    private String motivoReapertura;
}

