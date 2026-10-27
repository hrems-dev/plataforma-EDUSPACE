package pe.edu.eduspace.planificacion.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.eduspace.planificacion.dto.DocenteRequest;
import pe.edu.eduspace.planificacion.dto.DocenteResponse;
import pe.edu.eduspace.planificacion.dto.PageResponse;
import pe.edu.eduspace.planificacion.entity.Docente;
import pe.edu.eduspace.planificacion.entity.EstadoDocente;
import pe.edu.eduspace.planificacion.exception.BusinessRuleException;
import pe.edu.eduspace.planificacion.exception.ResourceNotFoundException;
import pe.edu.eduspace.planificacion.mapper.PlanificacionMapper;
import pe.edu.eduspace.planificacion.repository.AsignacionRepository;
import pe.edu.eduspace.planificacion.repository.DocenteRepository;
import pe.edu.eduspace.planificacion.repository.SeccionRepository;
import pe.edu.eduspace.planificacion.service.DocenteService;

@Service
@RequiredArgsConstructor
@Transactional
public class DocenteServiceImpl implements DocenteService {

    private final DocenteRepository repository;
    private final AsignacionRepository asignacionRepository;
    private final SeccionRepository seccionRepository;
    private final PlanificacionMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<DocenteResponse> buscar(String q, Pageable pageable) {
        String texto = q == null ? "" : q.trim();
        return PageResponse.of(repository.buscar(texto, pageable).map(mapper::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public DocenteResponse obtener(Long id) {
        return mapper.toResponse(buscar(id));
    }

    @Override
    public DocenteResponse crear(DocenteRequest request) {
        String tipo = tipoDocumento(request);
        if (repository.existsByTipoDocumentoAndNumeroDocumento(tipo, request.numeroDocumento())) {
            throw new BusinessRuleException("Ya existe un docente con " + tipo + " " + request.numeroDocumento());
        }
        Docente docente = mapper.toEntity(request);
        docente.setTipoDocumento(tipo);
        docente.setEstado(EstadoDocente.ACTIVO);
        return mapper.toResponse(repository.save(docente));
    }

    @Override
    public DocenteResponse actualizar(Long id, DocenteRequest request) {
        Docente docente = buscar(id);
        String tipo = tipoDocumento(request);
        if (repository.existsByTipoDocumentoAndNumeroDocumentoAndIdNot(tipo, request.numeroDocumento(), id)) {
            throw new BusinessRuleException("Ya existe otro docente con " + tipo + " " + request.numeroDocumento());
        }
        mapper.update(request, docente);
        docente.setTipoDocumento(tipo);
        return mapper.toResponse(repository.save(docente));
    }

    @Override
    public DocenteResponse vincularUsuario(Long id, Long usuarioId) {
        Docente docente = buscar(id);
        if (repository.existsByUsuarioIdAndIdNot(usuarioId, id)) {
            throw new BusinessRuleException("El usuario " + usuarioId + " ya está vinculado a otro docente");
        }
        docente.setUsuarioId(usuarioId);
        return mapper.toResponse(repository.save(docente));
    }

    @Override
    public void eliminar(Long id) {
        Docente docente = buscar(id);
        if (asignacionRepository.existsByDocenteId(id) || seccionRepository.countByTutorId(id) > 0) {
            docente.setEstado(EstadoDocente.INACTIVO);
            repository.save(docente);
        } else {
            repository.delete(docente);
        }
    }

    private String tipoDocumento(DocenteRequest request) {
        return request.tipoDocumento() == null || request.tipoDocumento().isBlank() ? "DNI" : request.tipoDocumento();
    }

    private Docente buscar(Long id) {
        return repository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Docente", id));
    }
}
