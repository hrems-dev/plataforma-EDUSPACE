package pe.edu.eduspace.planificacion.service.impl;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.eduspace.planificacion.dto.AulaRequest;
import pe.edu.eduspace.planificacion.dto.AulaResponse;
import pe.edu.eduspace.planificacion.entity.Aula;
import pe.edu.eduspace.planificacion.entity.TipoAula;
import pe.edu.eduspace.planificacion.exception.BusinessRuleException;
import pe.edu.eduspace.planificacion.exception.ResourceNotFoundException;
import pe.edu.eduspace.planificacion.mapper.PlanificacionMapper;
import pe.edu.eduspace.planificacion.repository.AulaRepository;
import pe.edu.eduspace.planificacion.repository.HorarioRepository;
import pe.edu.eduspace.planificacion.repository.SeccionRepository;
import pe.edu.eduspace.planificacion.service.AulaService;

@Service
@RequiredArgsConstructor
@Transactional
public class AulaServiceImpl implements AulaService {

    private final AulaRepository repository;
    private final SeccionRepository seccionRepository;
    private final HorarioRepository horarioRepository;
    private final PlanificacionMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public List<AulaResponse> listar(TipoAula tipo) {
        List<Aula> aulas = tipo == null ? repository.findAllByOrderByCodigo() : repository.findByTipoOrderByCodigo(tipo);
        return aulas.stream().map(mapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AulaResponse obtener(Long id) {
        return mapper.toResponse(buscar(id));
    }

    @Override
    public AulaResponse crear(AulaRequest request) {
        if (repository.existsByCodigo(request.codigo())) {
            throw new BusinessRuleException("Ya existe un aula con el código " + request.codigo());
        }
        Aula aula = mapper.toEntity(request);
        aula.setTipo(request.tipo() == null ? TipoAula.AULA : request.tipo());
        aula.setActivo(request.activo() == null || request.activo());
        return mapper.toResponse(repository.save(aula));
    }

    @Override
    public AulaResponse actualizar(Long id, AulaRequest request) {
        Aula aula = buscar(id);
        if (repository.existsByCodigoAndIdNot(request.codigo(), id)) {
            throw new BusinessRuleException("Ya existe un aula con el código " + request.codigo());
        }
        boolean desactivar = Boolean.FALSE.equals(request.activo()) && Boolean.TRUE.equals(aula.getActivo());
        if (desactivar && enUso(id)) {
            throw new BusinessRuleException("No se puede desactivar un aula usada en horarios o secciones vigentes");
        }
        mapper.update(request, aula);
        if (request.tipo() == null) {
            aula.setTipo(TipoAula.AULA);
        }
        if (request.activo() == null) {
            aula.setActivo(true);
        }
        return mapper.toResponse(repository.save(aula));
    }

    @Override
    public void eliminar(Long id) {
        Aula aula = buscar(id);
        if (seccionRepository.countByAulaId(id) > 0 || horarioRepository.existsByAulaId(id)) {
            throw new BusinessRuleException("El aula está en uso (secciones u horarios): desactívela en lugar de eliminarla");
        }
        repository.delete(aula);
    }

    private boolean enUso(Long aulaId) {
        return seccionRepository.countByAulaIdAndActivoTrue(aulaId) > 0 || horarioRepository.existsByAulaId(aulaId);
    }

    private Aula buscar(Long id) {
        return repository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Aula", id));
    }
}
