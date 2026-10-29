package pe.edu.eduspace.planificacion.service.impl;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.eduspace.planificacion.dto.MallaGradoResponse;
import pe.edu.eduspace.planificacion.dto.MallaItemResponse;
import pe.edu.eduspace.planificacion.dto.MallaLoteRequest;
import pe.edu.eduspace.planificacion.dto.MallaLoteResponse;
import pe.edu.eduspace.planificacion.dto.MallaRequest;
import java.util.ArrayList;
import pe.edu.eduspace.planificacion.entity.Curso;
import pe.edu.eduspace.planificacion.entity.Grado;
import pe.edu.eduspace.planificacion.entity.GradoCurso;
import pe.edu.eduspace.planificacion.exception.BusinessRuleException;
import pe.edu.eduspace.planificacion.exception.ResourceNotFoundException;
import pe.edu.eduspace.planificacion.mapper.PlanificacionMapper;
import pe.edu.eduspace.planificacion.repository.AsignacionRepository;
import pe.edu.eduspace.planificacion.repository.CursoRepository;
import pe.edu.eduspace.planificacion.repository.GradoCursoRepository;
import pe.edu.eduspace.planificacion.repository.GradoRepository;
import pe.edu.eduspace.planificacion.service.MallaService;

@Service
@RequiredArgsConstructor
@Transactional
public class MallaServiceImpl implements MallaService {

    private final GradoCursoRepository repository;
    private final GradoRepository gradoRepository;
    private final CursoRepository cursoRepository;
    private final AsignacionRepository asignacionRepository;
    private final PlanificacionMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public MallaGradoResponse porGrado(Long gradoId) {
        Grado grado = gradoRepository.findById(gradoId).orElseThrow(() -> ResourceNotFoundException.of("Grado", gradoId));
        List<MallaItemResponse> cursos = repository.findByGradoIdOrderByCursoNombre(gradoId).stream()
                .map(mapper::toResponse).toList();
        int total = cursos.stream().mapToInt(c -> c.horasSemanales()).sum();
        return new MallaGradoResponse(grado.getId(), grado.getNombre(), total, cursos);
    }

    @Override
    public MallaItemResponse agregar(MallaRequest request) {
        Grado grado = gradoRepository.findById(request.gradoId())
                .orElseThrow(() -> ResourceNotFoundException.of("Grado", request.gradoId()));
        Curso curso = cursoRepository.findById(request.cursoId())
                .orElseThrow(() -> ResourceNotFoundException.of("Curso", request.cursoId()));
        if (repository.existsByGradoIdAndCursoId(grado.getId(), curso.getId())) {
            throw new BusinessRuleException("El curso " + curso.getNombre() + " ya está en la malla de " + grado.getNombre());
        }
        GradoCurso item = new GradoCurso();
        item.setGrado(grado);
        item.setCurso(curso);
        item.setHorasSemanales(request.horasSemanales());
        return mapper.toResponse(repository.save(item));
    }

    @Override
    public MallaLoteResponse agregarLote(MallaLoteRequest request) {
        boolean sobrescribir = Boolean.TRUE.equals(request.sobrescribir());
        int creados = 0;
        int actualizados = 0;
        int omitidos = 0;
        List<String> detalle = new ArrayList<>();
        for (Long gradoId : request.gradoIds().stream().distinct().toList()) {
            Grado grado = gradoRepository.findById(gradoId).orElseThrow(() -> ResourceNotFoundException.of("Grado", gradoId));
            for (MallaLoteRequest.ItemLote it : request.cursos()) {
                Curso curso = cursoRepository.findById(it.cursoId()).orElseThrow(() -> ResourceNotFoundException.of("Curso", it.cursoId()));
                var existente = repository.findByGradoIdAndCursoId(grado.getId(), curso.getId());
                if (existente.isEmpty()) {
                    GradoCurso item = new GradoCurso();
                    item.setGrado(grado);
                    item.setCurso(curso);
                    item.setHorasSemanales(it.horasSemanales());
                    repository.save(item);
                    creados++;
                } else if (sobrescribir && !existente.get().getHorasSemanales().equals(it.horasSemanales())) {
                    existente.get().setHorasSemanales(it.horasSemanales());
                    repository.save(existente.get());
                    actualizados++;
                } else {
                    omitidos++;
                    detalle.add(curso.getNombre() + " ya estaba en " + grado.getNombre());
                }
            }
        }
        return new MallaLoteResponse(creados, actualizados, omitidos, detalle);
    }

    @Override
    public MallaItemResponse actualizarHoras(Long id, Short horasSemanales) {
        GradoCurso item = buscar(id);
        item.setHorasSemanales(horasSemanales);
        return mapper.toResponse(repository.save(item));
    }

    @Override
    public void eliminar(Long id) {
        GradoCurso item = buscar(id);
        if (asignacionRepository.existsByGradoAndCurso(item.getGrado().getId(), item.getCurso().getId())) {
            throw new BusinessRuleException("El curso está asignado a docentes en secciones de este grado: elimine esas asignaciones primero");
        }
        repository.delete(item);
    }

    private GradoCurso buscar(Long id) {
        return repository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Elemento de la malla", id));
    }
}
