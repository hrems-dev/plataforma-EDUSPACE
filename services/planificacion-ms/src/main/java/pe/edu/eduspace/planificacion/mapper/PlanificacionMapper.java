package pe.edu.eduspace.planificacion.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import pe.edu.eduspace.planificacion.dto.*;
import pe.edu.eduspace.planificacion.entity.*;

/** Conversiones entidad ↔ DTO. Las relaciones y el estado los resuelven los servicios, no el mapper. */
@Mapper
public interface PlanificacionMapper {

    // ---- Año ----
    AnioResponse toResponse(AnioAcademico anio);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "estado", ignore = true)
    @Mapping(target = "creadoEn", ignore = true)
    @Mapping(target = "actualizadoEn", ignore = true)
    AnioAcademico toEntity(AnioRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "estado", ignore = true)
    @Mapping(target = "creadoEn", ignore = true)
    @Mapping(target = "actualizadoEn", ignore = true)
    void update(AnioRequest request, @MappingTarget AnioAcademico anio);

    // ---- Periodo ----
    @Mapping(source = "anio.id", target = "anioId")
    PeriodoResponse toResponse(Periodo periodo);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "anio", ignore = true)
    @Mapping(target = "estado", ignore = true)
    Periodo toEntity(PeriodoRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "anio", ignore = true)
    @Mapping(target = "estado", ignore = true)
    void update(PeriodoRequest request, @MappingTarget Periodo periodo);

    // ---- Nivel / Grado ----
    NivelResponse toResponse(Nivel nivel);

    @Mapping(source = "nivel.id", target = "nivelId")
    @Mapping(source = "nivel.nombre", target = "nivelNombre")
    GradoResponse toResponse(Grado grado);

    // ---- Aula ----
    AulaResponse toResponse(Aula aula);

    @Mapping(target = "id", ignore = true)
    Aula toEntity(AulaRequest request);

    @Mapping(target = "id", ignore = true)
    void update(AulaRequest request, @MappingTarget Aula aula);

    // ---- Sección ----
    @Mapping(source = "anio.id", target = "anioId")
    @Mapping(source = "anio.nombre", target = "anioNombre")
    @Mapping(source = "grado.id", target = "gradoId")
    @Mapping(source = "grado.nombre", target = "gradoNombre")
    @Mapping(source = "grado.nivel.id", target = "nivelId")
    @Mapping(source = "aula.id", target = "aulaId")
    @Mapping(source = "aula.nombre", target = "aulaNombre")
    @Mapping(source = "tutor.id", target = "tutorDocenteId")
    @Mapping(target = "tutorNombre", expression = "java(seccion.getTutor() == null ? null : seccion.getTutor().nombreCompleto())")
    SeccionResponse toResponse(Seccion seccion);

    @Mapping(source = "anio.id", target = "anioId")
    @Mapping(source = "grado.id", target = "gradoId")
    @Mapping(source = "grado.nivel.id", target = "nivelId")
    @Mapping(source = "tutor.id", target = "tutorDocenteId")
    SeccionInternaResponse toInterna(Seccion seccion);

    // ---- Curso / Malla ----
    CursoResponse toResponse(Curso curso);

    @Mapping(target = "id", ignore = true)
    Curso toEntity(CursoRequest request);

    @Mapping(target = "id", ignore = true)
    void update(CursoRequest request, @MappingTarget Curso curso);

    @Mapping(source = "curso.id", target = "cursoId")
    @Mapping(source = "curso.codigo", target = "cursoCodigo")
    @Mapping(source = "curso.nombre", target = "cursoNombre")
    MallaItemResponse toResponse(GradoCurso gradoCurso);

    // ---- Docente ----
    DocenteResponse toResponse(Docente docente);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "usuarioId", ignore = true)
    @Mapping(target = "estado", ignore = true)
    @Mapping(target = "creadoEn", ignore = true)
    @Mapping(target = "actualizadoEn", ignore = true)
    Docente toEntity(DocenteRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "usuarioId", ignore = true)
    @Mapping(target = "estado", ignore = true)
    @Mapping(target = "creadoEn", ignore = true)
    @Mapping(target = "actualizadoEn", ignore = true)
    void update(DocenteRequest request, @MappingTarget Docente docente);

    // ---- Asignación ----
    @Mapping(source = "seccion.id", target = "seccionId")
    @Mapping(target = "seccionNombre", expression = "java(a.getSeccion().getGrado().getNombre() + \" \" + a.getSeccion().getNombre())")
    @Mapping(source = "curso.id", target = "cursoId")
    @Mapping(source = "curso.nombre", target = "cursoNombre")
    @Mapping(source = "docente.id", target = "docenteId")
    @Mapping(target = "docenteNombre", expression = "java(a.getDocente().nombreCompleto())")
    AsignacionResponse toResponse(Asignacion a);

    // ---- Bloques ----
    BloqueResponse toResponse(BloqueHorario bloque);

    @Mapping(target = "id", ignore = true)
    BloqueHorario toEntity(BloqueRequest request);

    @Mapping(target = "id", ignore = true)
    void update(BloqueRequest request, @MappingTarget BloqueHorario bloque);

    // ---- Horario ----
    @Mapping(source = "asignacion.id", target = "asignacionId")
    @Mapping(source = "asignacion.seccion.id", target = "seccionId")
    @Mapping(target = "seccionNombre", expression = "java(h.getAsignacion().getSeccion().getGrado().getNombre() + \" \" + h.getAsignacion().getSeccion().getNombre())")
    @Mapping(source = "asignacion.curso.id", target = "cursoId")
    @Mapping(source = "asignacion.curso.nombre", target = "cursoNombre")
    @Mapping(source = "asignacion.docente.id", target = "docenteId")
    @Mapping(target = "docenteNombre", expression = "java(h.getAsignacion().getDocente().nombreCompleto())")
    @Mapping(source = "bloque.id", target = "bloqueId")
    @Mapping(source = "bloque.orden", target = "bloqueOrden")
    @Mapping(source = "bloque.horaInicio", target = "horaInicio")
    @Mapping(source = "bloque.horaFin", target = "horaFin")
    @Mapping(source = "aula.id", target = "aulaId")
    @Mapping(source = "aula.nombre", target = "aulaNombre")
    HorarioResponse toResponse(Horario h);

    // ---- Publicación / Feriado ----
    @Mapping(source = "seccion.id", target = "seccionId")
    PublicacionResponse toResponse(PublicacionHorario publicacion);

    @Mapping(source = "anio.id", target = "anioId")
    FeriadoResponse toResponse(Feriado feriado);
}
