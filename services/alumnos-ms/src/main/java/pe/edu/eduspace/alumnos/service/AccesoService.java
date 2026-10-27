package pe.edu.eduspace.alumnos.service;

/** Regla fina de propiedad: ALUMNO y APODERADO solo acceden a lo propio (el rol grueso lo aplica la anotación del endpoint). */
public interface AccesoService {

    /** Personal del colegio, o el propio alumno, o un apoderado del alumno. Si no, AccessDeniedException. */
    void verificarLecturaAlumno(Long alumnoId);

    /** Personal del colegio, o el propio apoderado. Si no, AccessDeniedException. */
    void verificarLecturaApoderado(Long apoderadoId);
}
