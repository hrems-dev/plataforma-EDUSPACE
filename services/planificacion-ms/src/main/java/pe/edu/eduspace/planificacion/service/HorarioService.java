package pe.edu.eduspace.planificacion.service;

import pe.edu.eduspace.planificacion.dto.*;

public interface HorarioService {
    HorarioSeccionResponse porSeccion(Long seccionId);

    HorarioDocenteResponse porDocente(Long docenteId);

    /** Todas las clases del año (para ver qué aulas están ocupadas y cuáles libres en cada bloque). */
    java.util.List<HorarioResponse> porAnio(Long anioId);

    HorarioResponse crear(HorarioRequest request);

    HorarioResponse mover(Long id, HorarioRequest request);

    void eliminar(Long id);

    /** Validación global de la sección: choques y cursos con horas faltantes o sobrantes. */
    ResultadoValidacionHorario validar(Long seccionId);
}
