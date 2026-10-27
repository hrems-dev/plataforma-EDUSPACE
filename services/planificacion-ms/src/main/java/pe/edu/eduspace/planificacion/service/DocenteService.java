package pe.edu.eduspace.planificacion.service;

import org.springframework.data.domain.Pageable;
import pe.edu.eduspace.planificacion.dto.DocenteRequest;
import pe.edu.eduspace.planificacion.dto.DocenteResponse;
import pe.edu.eduspace.planificacion.dto.PageResponse;

public interface DocenteService {
    PageResponse<DocenteResponse> buscar(String q, Pageable pageable);

    DocenteResponse obtener(Long id);

    DocenteResponse crear(DocenteRequest request);

    DocenteResponse actualizar(Long id, DocenteRequest request);

    DocenteResponse vincularUsuario(Long id, Long usuarioId);

    /** Inactiva al docente si tiene asignaciones o tutorías; si no, lo elimina. */
    void eliminar(Long id);
}
