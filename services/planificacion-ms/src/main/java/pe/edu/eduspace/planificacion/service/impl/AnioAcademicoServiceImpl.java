package pe.edu.eduspace.planificacion.service.impl;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.eduspace.planificacion.dto.AnioRequest;
import pe.edu.eduspace.planificacion.dto.AnioResponse;
import pe.edu.eduspace.planificacion.entity.AnioAcademico;
import pe.edu.eduspace.planificacion.entity.EstadoPeriodo;
import pe.edu.eduspace.planificacion.event.AnioActivadoEvent;
import pe.edu.eduspace.planificacion.exception.BusinessRuleException;
import pe.edu.eduspace.planificacion.exception.ResourceNotFoundException;
import pe.edu.eduspace.planificacion.mapper.PlanificacionMapper;
import pe.edu.eduspace.planificacion.repository.AnioAcademicoRepository;
import pe.edu.eduspace.planificacion.service.AnioAcademicoService;

@Service
@RequiredArgsConstructor
@Transactional
public class AnioAcademicoServiceImpl implements AnioAcademicoService {

    private final AnioAcademicoRepository repository;
    private final PlanificacionMapper mapper;
    private final ApplicationEventPublisher eventos;

    @Override
    @Transactional(readOnly = true)
    public List<AnioResponse> listar() {
        return repository.findAll(Sort.by(Sort.Direction.DESC, "fechaInicio")).stream().map(mapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AnioResponse obtener(Long id) {
        return mapper.toResponse(buscar(id));
    }

    @Override
    @Transactional(readOnly = true)
    public AnioResponse activo() {
        return repository.findFirstByEstado(EstadoPeriodo.ACTIVO).map(mapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("No hay un año académico ACTIVO"));
    }

    @Override
    public AnioResponse crear(AnioRequest request) {
        validarFechas(request);
        if (repository.existsByNombre(request.nombre())) {
            throw new BusinessRuleException("Ya existe un año académico con el nombre " + request.nombre());
        }
        return mapper.toResponse(repository.save(mapper.toEntity(request)));
    }

    @Override
    public AnioResponse actualizar(Long id, AnioRequest request) {
        AnioAcademico anio = buscar(id);
        if (anio.getEstado() == EstadoPeriodo.CERRADO) {
            throw new BusinessRuleException("Un año CERRADO es de solo lectura");
        }
        validarFechas(request);
        if (repository.existsByNombreAndIdNot(request.nombre(), id)) {
            throw new BusinessRuleException("Ya existe un año académico con el nombre " + request.nombre());
        }
        mapper.update(request, anio);
        return mapper.toResponse(repository.save(anio));
    }

    @Override
    public AnioResponse activar(Long id) {
        AnioAcademico anio = buscar(id);
        if (anio.getEstado() == EstadoPeriodo.ACTIVO) {
            throw new BusinessRuleException("El año " + anio.getNombre() + " ya está ACTIVO");
        }
        if (anio.getEstado() == EstadoPeriodo.CERRADO) {
            throw new BusinessRuleException("Un año CERRADO no puede volver a activarse");
        }
        repository.findFirstByEstado(EstadoPeriodo.ACTIVO).ifPresent(activo -> {
            throw new BusinessRuleException("El año " + activo.getNombre()
                    + " sigue ACTIVO: ciérrelo antes de activar otro");
        });
        anio.setEstado(EstadoPeriodo.ACTIVO);
        repository.saveAndFlush(anio);
        eventos.publishEvent(AnioActivadoEvent.of(anio.getId(), anio.getNombre(), anio.getFechaInicio(), anio.getFechaFin()));
        return mapper.toResponse(anio);
    }

    @Override
    public AnioResponse cerrar(Long id) {
        AnioAcademico anio = buscar(id);
        if (anio.getEstado() != EstadoPeriodo.ACTIVO) {
            throw new BusinessRuleException("Solo se puede cerrar un año ACTIVO");
        }
        anio.setEstado(EstadoPeriodo.CERRADO);
        return mapper.toResponse(repository.save(anio));
    }

    private AnioAcademico buscar(Long id) {
        return repository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Año académico", id));
    }

    private void validarFechas(AnioRequest request) {
        if (!request.fechaFin().isAfter(request.fechaInicio())) {
            throw new BusinessRuleException("La fecha de fin debe ser posterior a la fecha de inicio");
        }
    }
}
