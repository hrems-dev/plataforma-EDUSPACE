package pe.edu.upeu.eduspace.calificaciones.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerarBoletinRequest {

    private Long seccionId;
    private Long periodoId; // null para boletín anual
    private Long alumnoId;  // opcional, para generar solo a un alumno
    private Long anioId;
}

