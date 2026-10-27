package pe.edu.eduspace.alumnos.service;

import java.util.List;
import pe.edu.eduspace.alumnos.dto.AlumnoResumenResponse;
import pe.edu.eduspace.alumnos.dto.CambioEstadoRequest;
import pe.edu.eduspace.alumnos.dto.HistorialResponse;

public interface EstadoAlumnoService {
    AlumnoResumenResponse cambiar(Long alumnoId, CambioEstadoRequest request, Long usuarioId);

    List<HistorialResponse> historial(Long alumnoId);
}
