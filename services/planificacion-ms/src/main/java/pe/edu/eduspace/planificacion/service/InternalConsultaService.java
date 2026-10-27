package pe.edu.eduspace.planificacion.service;

import java.util.List;
import pe.edu.eduspace.planificacion.dto.*;

/** Lecturas livianas para otros servicios (adenda A2); sin paginación pesada. */
public interface InternalConsultaService {
    InternalDocenteResponse docentePorUsuario(Long usuarioId);

    List<AsignacionInternaResponse> asignacionesDeUsuario(Long usuarioId, Long anioId);

    boolean asignacionValida(Long docenteId, Long seccionId, Long cursoId);

    SeccionInternaResponse seccion(Long id);

    List<SeccionInternaResponse> secciones(Long anioId, Long gradoId);

    GradoResponse grado(Long id);

    NivelResponse nivel(Long id);

    List<PeriodoResponse> periodos(Long anioId);

    PeriodoResponse periodo(Long id);
}
