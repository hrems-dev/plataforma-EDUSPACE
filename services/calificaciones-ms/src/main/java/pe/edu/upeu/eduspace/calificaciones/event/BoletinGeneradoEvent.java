package pe.edu.upeu.eduspace.calificaciones.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BoletinGeneradoEvent {

    private Long boletinId;
    private Long alumnoId;
    private Long matriculaId;
    private Long periodoId;
    private String periodoNombre;
}

