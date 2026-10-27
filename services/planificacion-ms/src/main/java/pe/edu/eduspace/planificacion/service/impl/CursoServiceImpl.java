package pe.edu.eduspace.planificacion.service.impl;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.eduspace.planificacion.dto.CursoRequest;
import pe.edu.eduspace.planificacion.dto.CursoResponse;
import pe.edu.eduspace.planificacion.entity.Curso;
import pe.edu.eduspace.planificacion.exception.BusinessRuleException;
import pe.edu.eduspace.planificacion.exception.ResourceNotFoundException;
import pe.edu.eduspace.planificacion.mapper.PlanificacionMapper;
import pe.edu.eduspace.planificacion.repository.CursoRepository;
import pe.edu.eduspace.planificacion.repository.GradoCursoRepository;
import pe.edu.eduspace.planificacion.service.CursoService;

@Service
@RequiredArgsConstructor
@Transactional
public class CursoServiceImpl implements CursoService {

    private final CursoRepository repository;
    private final GradoCursoRepository mallaRepository;
    private final PlanificacionMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public List<CursoResponse> listar() {
        return repository.findAllByOrderByNombre().stream().map(mapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CursoResponse> porIds(List<Long> ids) {
        return repository.findByIdIn(ids).stream().map(mapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CursoResponse obtener(Long id) {
        return mapper.toResponse(buscar(id));
    }

    @Override
    public CursoResponse crear(CursoRequest request) {
        if (repository.existsByCodigo(request.codigo())) {
            throw new BusinessRuleException("Ya existe un curso con el código " + request.codigo());
        }
        Curso curso = mapper.toEntity(request);
        curso.setActivo(request.activo() == null || request.activo());
        return mapper.toResponse(repository.save(curso));
    }

    @Override
    public CursoResponse actualizar(Long id, CursoRequest request) {
        Curso curso = buscar(id);
        if (repository.existsByCodigoAndIdNot(request.codigo(), id)) {
            throw new BusinessRuleException("Ya existe un curso con el código " + request.codigo());
        }
        mapper.update(request, curso);
        if (request.activo() == null) {
            curso.setActivo(true);
        }
        return mapper.toResponse(repository.save(curso));
    }

    @Override
    public void eliminar(Long id) {
        Curso curso = buscar(id);
        if (mallaRepository.countByCursoId(id) > 0) {
            throw new BusinessRuleException("El curso forma parte de la malla de algún grado: quítelo de la malla primero");
        }
        repository.delete(curso);
    }

    private Curso buscar(Long id) {
        return repository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Curso", id));
    }
}
