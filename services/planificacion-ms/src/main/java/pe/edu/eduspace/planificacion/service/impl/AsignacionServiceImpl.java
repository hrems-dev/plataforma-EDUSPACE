package pe.edu.eduspace.planificacion.service.impl;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.eduspace.planificacion.dto.AsignacionRequest;
import pe.edu.eduspace.planificacion.dto.AsignacionResponse;
import pe.edu.eduspace.planificacion.entity.*;
import pe.edu.eduspace.planificacion.exception.BusinessRuleException;
import pe.edu.eduspace.planificacion.exception.ResourceNotFoundException;
import pe.edu.eduspace.planificacion.mapper.PlanificacionMapper;
import pe.edu.eduspace.planificacion.repository.*;
import pe.edu.eduspace.planificacion.service.AsignacionService;

@Service
@RequiredArgsConstructor
@Transactional
public class AsignacionServiceImpl implements AsignacionService {

    private final AsignacionRepository repository;
    private final SeccionRepository seccionRepository;
    private final CursoRepository cursoRepository;
    private final DocenteRepository docenteRepository;
    private final GradoCursoRepository mallaRepository;
    private final HorarioRepository horarioRepository;
    private final PlanificacionMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public List<AsignacionResponse> listarPorSeccion(Long seccionId) {
        return repository.findBySeccionId(seccionId).stream().map(mapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AsignacionResponse> listarPorDocente(Long docenteId) {
        return repository.findByDocenteId(docenteId).stream().map(mapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AsignacionResponse obtener(Long id) {
        return mapper.toResponse(buscar(id));
    }

    @Override
    public AsignacionResponse crear(AsignacionRequest request) {
        Seccion seccion = seccionRepository.findById(request.seccionId())
                .orElseThrow(() -> ResourceNotFoundException.of("Sección", request.seccionId()));
        anioEditable(seccion);
        Curso curso = cursoRepository.findById(request.cursoId())
                .orElseThrow(() -> ResourceNotFoundException.of("Curso", request.cursoId()));
        Docente docente = docenteActivo(request.docenteId());
        if (!mallaRepository.existsByGradoIdAndCursoId(seccion.getGrado().getId(), curso.getId())) {
            throw new BusinessRuleException("El curso " + curso.getNombre() + " no está en la malla de " + seccion.getGrado().getNombre());
        }
        if (repository.existsBySeccionIdAndCursoId(seccion.getId(), curso.getId())) {
            throw new BusinessRuleException("El curso " + curso.getNombre() + " ya tiene docente asignado en esta sección");
        }
        Asignacion asignacion = new Asignacion();
        asignacion.setSeccion(seccion);
        asignacion.setCurso(curso);
        asignacion.setDocente(docente);
        return mapper.toResponse(repository.save(asignacion));
    }

    @Override
    public AsignacionResponse cambiarDocente(Long id, Long docenteId) {
        Asignacion asignacion = buscar(id);
        anioEditable(asignacion.getSeccion());
        Docente docente = docenteActivo(docenteId);
        for (Horario h : horarioRepository.findBySeccion(asignacion.getSeccion().getId())) {
            if (h.getAsignacion().getId().equals(id)
                    && horarioRepository.existsChoqueDocente(docenteId, h.getDiaSemana(), h.getBloque().getId(), null)) {
                throw new BusinessRuleException("El docente " + docente.nombreCompleto() + " ya tiene clase en el bloque "
                        + h.getBloque().getOrden() + " del día " + h.getDiaSemana() + ": no se puede reasignar");
            }
        }
        asignacion.setDocente(docente);
        return mapper.toResponse(repository.save(asignacion));
    }

    @Override
    public void eliminar(Long id) {
        Asignacion asignacion = buscar(id);
        anioEditable(asignacion.getSeccion());
        if (horarioRepository.countByAsignacionId(id) > 0) {
            throw new BusinessRuleException("La asignación tiene clases en el horario: elimínelas primero");
        }
        repository.delete(asignacion);
    }

    private Docente docenteActivo(Long docenteId) {
        Docente docente = docenteRepository.findById(docenteId)
                .orElseThrow(() -> ResourceNotFoundException.of("Docente", docenteId));
        if (docente.getEstado() != EstadoDocente.ACTIVO) {
            throw new BusinessRuleException("El docente " + docente.nombreCompleto() + " está INACTIVO");
        }
        return docente;
    }

    private void anioEditable(Seccion seccion) {
        if (seccion.getAnio().getEstado() == EstadoPeriodo.CERRADO) {
            throw new BusinessRuleException("El año " + seccion.getAnio().getNombre() + " está CERRADO (solo lectura)");
        }
    }

    private Asignacion buscar(Long id) {
        return repository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Asignación", id));
    }
}
