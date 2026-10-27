package pe.edu.eduspace.alumnos.mapper;

import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import pe.edu.eduspace.alumnos.dto.*;
import pe.edu.eduspace.alumnos.entity.*;

/** Conversiones entidad ↔ DTO. La entidad nunca se expone. */
@Mapper
public interface AlumnoMapper {

    // ---- Alumno ----
    @Mapping(target = "nombreCompleto", expression = "java(alumno.nombreCompleto())")
    AlumnoResponse toResponse(Alumno alumno, List<VinculoResponse> apoderados);

    @Mapping(target = "nombreCompleto", expression = "java(alumno.nombreCompleto())")
    AlumnoResumenResponse toResumen(Alumno alumno);

    @Mapping(target = "nombreCompleto", expression = "java(alumno.nombreCompleto())")
    AlumnoBasicoResponse toBasico(Alumno alumno);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "codigo", ignore = true)
    @Mapping(target = "usuarioId", ignore = true)
    @Mapping(target = "fotoUrl", ignore = true)
    @Mapping(target = "estado", ignore = true)
    @Mapping(target = "creadoEn", ignore = true)
    @Mapping(target = "actualizadoEn", ignore = true)
    Alumno toEntity(AlumnoCreateRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "codigo", ignore = true)
    @Mapping(target = "usuarioId", ignore = true)
    @Mapping(target = "fotoUrl", ignore = true)
    @Mapping(target = "estado", ignore = true)
    @Mapping(target = "creadoEn", ignore = true)
    @Mapping(target = "actualizadoEn", ignore = true)
    void update(AlumnoUpdateRequest request, @MappingTarget Alumno alumno);

    // ---- Apoderado / vínculo ----
    ApoderadoResponse toResponse(Apoderado apoderado);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "usuarioId", ignore = true)
    @Mapping(target = "creadoEn", ignore = true)
    @Mapping(target = "actualizadoEn", ignore = true)
    Apoderado toEntity(ApoderadoRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "usuarioId", ignore = true)
    @Mapping(target = "creadoEn", ignore = true)
    @Mapping(target = "actualizadoEn", ignore = true)
    void update(ApoderadoRequest request, @MappingTarget Apoderado apoderado);

    VinculoResponse toResponse(AlumnoApoderado vinculo);

    @Mapping(source = "alumno.id", target = "alumnoId")
    @Mapping(source = "alumno.codigo", target = "codigo")
    @Mapping(target = "nombreCompleto", expression = "java(vinculo.getAlumno().nombreCompleto())")
    @Mapping(source = "alumno.estado", target = "estado")
    HijoResponse toHijo(AlumnoApoderado vinculo);

    @Mapping(source = "apoderado.id", target = "apoderadoId")
    @Mapping(source = "apoderado.usuarioId", target = "usuarioId")
    @Mapping(target = "nombreCompleto", expression = "java(vinculo.getApoderado().nombreCompleto())")
    @Mapping(source = "apoderado.email", target = "email")
    @Mapping(source = "apoderado.telefono", target = "telefono")
    ApoderadoContactoResponse toContacto(AlumnoApoderado vinculo);

    // ---- Documentos / salud / historial ----
    @Mapping(source = "alumno.id", target = "alumnoId")
    DocumentoResponse toResponse(AlumnoDocumento documento);

    SaludResponse toResponse(AlumnoSalud salud);

    @Mapping(target = "alumnoId", ignore = true)
    @Mapping(target = "alumno", ignore = true)
    @Mapping(target = "actualizadoEn", ignore = true)
    void update(SaludRequest request, @MappingTarget AlumnoSalud salud);

    HistorialResponse toResponse(AlumnoEstadoHistorial historial);
}
