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
public class AsistenciaFaltaRegistradaEvent {
    private Long asistenciaId;          // sesión
    private Long asistenciaDetalleId;
    private Long alumnoId;
    private Long matriculaId;
    private Long seccionId;
    private LocalDate fecha;
}
