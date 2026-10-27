package pe.edu.eduspace.alumnos.service;

import java.util.List;
import org.springframework.data.domain.Pageable;
import pe.edu.eduspace.alumnos.dto.*;
import pe.edu.eduspace.alumnos.entity.EstadoAlumno;

public interface AlumnoService {
    PageResponse<AlumnoResumenResponse> buscar(String q, EstadoAlumno estado, Pageable pageable);

    AlumnoResponse obtener(Long id);

    AlumnoResponse crear(AlumnoCreateRequest request);

    AlumnoResponse actualizar(Long id, AlumnoUpdateRequest request);

    AlumnoResponse vincularUsuario(Long id, Long usuarioId);

    /** Alumnos del usuario autenticado: sus hijos (APODERADO) o él mismo (ALUMNO). */
    List<HijoResponse> mios(Long usuarioId, boolean esApoderado);
}
