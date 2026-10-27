package pe.edu.eduspace.planificacion.service.impl;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.eduspace.planificacion.dto.*;
import pe.edu.eduspace.planificacion.entity.Docente;
import pe.edu.eduspace.planificacion.exception.ResourceNotFoundException;
import pe.edu.eduspace.planificacion.mapper.PlanificacionMapper;
import pe.edu.eduspace.planificacion.repository.*;
import pe.edu.eduspace.planificacion.service.InternalConsultaService;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InternalConsultaServiceImpl implements InternalConsultaService {

    private final DocenteRepository docenteRepository;
    private final AsignacionRepository asignacionRepository;
    private final SeccionRepository seccionRepository;
    private final GradoRepository gradoRepository;
    private final NivelRepository nivelRepository;
    private final PeriodoRepository periodoRepository;
    private final PlanificacionMapper mapper;

    @Override
    public InternalDocenteResponse docentePorUsuario(Long usuarioId) {
        Docente d = docenteRepository.findByUsuarioId(usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("El usuario " + usuarioId + " no está vinculado a un docente"));
        return new InternalDocenteResponse(d.getId(), d.nombreCompleto(), d.getEstado());
    }

    @Override
    public List<AsignacionInternaResponse> asignacionesDeUsuario(Long usuarioId, Long anioId) {
        Docente d = docenteRepository.findByUsuarioId(usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("El usuario " + usuarioId + " no está vinculado a un docente"));
        return asignacionRepository.findByDocenteAndAnio(d.getId(), anioId).stream()
                .map(a -> new AsignacionInternaResponse(a.getSeccion().getId(), a.getCurso().getId(), a.getId())).toList();
    }

    @Override
    public boolean asignacionValida(Long docenteId, Long seccionId, Long cursoId) {
        return asignacionRepository.existsByDocenteIdAndSeccionIdAndCursoId(docenteId, seccionId, cursoId);
    }

    @Override
    public SeccionInternaResponse seccion(Long id) {
        return seccionRepository.findById(id).map(mapper::toInterna)
                .orElseThrow(() -> ResourceNotFoundException.of("Sección", id));
    }

    @Override
    public List<SeccionInternaResponse> secciones(Long anioId, Long gradoId) {
        return seccionRepository.buscar(anioId, gradoId).stream().map(mapper::toInterna).toList();
    }

    @Override
    public GradoResponse grado(Long id) {
        return gradoRepository.findById(id).map(mapper::toResponse)
                .orElseThrow(() -> ResourceNotFoundException.of("Grado", id));
    }

    @Override
    public NivelResponse nivel(Long id) {
        return nivelRepository.findById(id).map(mapper::toResponse)
                .orElseThrow(() -> ResourceNotFoundException.of("Nivel", id));
    }

    @Override
    public List<PeriodoResponse> periodos(Long anioId) {
        return periodoRepository.findByAnioIdOrderByNumero(anioId).stream().map(mapper::toResponse).toList();
    }

    @Override
    public PeriodoResponse periodo(Long id) {
        return periodoRepository.findById(id).map(mapper::toResponse)
                .orElseThrow(() -> ResourceNotFoundException.of("Periodo", id));
    }
}
