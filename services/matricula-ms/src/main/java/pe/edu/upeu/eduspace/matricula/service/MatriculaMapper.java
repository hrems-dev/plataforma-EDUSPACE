package pe.edu.upeu.eduspace.matricula.service;

import pe.edu.upeu.eduspace.matricula.dto.Dtos.*;
import pe.edu.upeu.eduspace.matricula.entity.*;

public final class MatriculaMapper {

    private MatriculaMapper() {}

    public static MatriculaResponse toResponse(Matricula m) {
        return new MatriculaResponse(m.getId(), m.getCodigo(), m.getAlumnoId(), m.getAlumnoNombre(), m.getAnioId(),
            m.getNivelId(), m.getGradoId(), m.getSeccionId(), m.getCampaniaId(), m.getTipo(), m.getEstado(),
            m.getMatriculaAnteriorId(), m.getVenceEn(), m.getObservaciones(), m.getCreadoEn(), m.getConfirmadaEn(),
            m.getCerradaEn(), m.getMotivoCierre());
    }

    public static RequisitoResponse toResponse(MatriculaRequisito r) {
        return new RequisitoResponse(r.getId(), r.getTipo(), r.getCumplido(), r.getObservacion(), r.getVerificadoPor(),
            r.getVerificadoEn());
    }

    public static HistorialResponse toResponse(MatriculaHistorial h) {
        return new HistorialResponse(h.getId(), h.getTipoCambio(), h.getEstadoAnterior(), h.getEstadoNuevo(),
            h.getSeccionAnteriorId(), h.getSeccionNuevaId(), h.getMotivo(), h.getUsuarioId(), h.getFecha());
    }

    public static CupoResponse toResponse(CupoSeccion c) {
        return new CupoResponse(c.getSeccionId(), c.getSeccionNombre(), c.getAnioId(), c.getGradoId(), c.getCapacidad(),
            c.getOcupados(), c.getCapacidad() - c.getOcupados());
    }

    public static CampaniaResponse toResponse(CampaniaMatricula c) {
        return new CampaniaResponse(c.getId(), c.getAnioId(), c.getNombre(), c.getFechaInicio(), c.getFechaFin(), c.getEstado());
    }
}
