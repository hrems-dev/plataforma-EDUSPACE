package pe.edu.upeu.eduspace.asistencia.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JustificacionResueltaEvent {
    private Long justificacionId;
    private Long alumnoId;
    private Long matriculaId;
    private String resultado;           // APROBADA | RECHAZADA
    private LocalDate fechaDesde;
    private LocalDate fechaHasta;
}
