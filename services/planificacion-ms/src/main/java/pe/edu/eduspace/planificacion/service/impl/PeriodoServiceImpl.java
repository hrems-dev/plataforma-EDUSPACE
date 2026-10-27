package pe.edu.eduspace.planificacion.service.impl;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.eduspace.planificacion.dto.PeriodoRequest;
import pe.edu.eduspace.planificacion.dto.PeriodoResponse;
import pe.edu.eduspace.planificacion.entity.AnioAcademico;
import pe.edu.eduspace.planificacion.entity.EstadoPeriodo;
import pe.edu.eduspace.planificacion.entity.Periodo;
import pe.edu.eduspace.planificacion.event.PeriodoCambiadoEvent;
import pe.edu.eduspace.planificacion.exception.BusinessRuleException;
import pe.edu.eduspace.planificacion.exception.ResourceNotFoundException;
import pe.edu.eduspace.planificacion.mapper.PlanificacionMapper;
import pe.edu.eduspace.planificacion.repository.AnioAcademicoRepository;
import pe.edu.eduspace.planificacion.repository.PeriodoRepository;
import pe.edu.eduspace.planificacion.service.PeriodoService;

@Service
@RequiredArgsConstructor
@Transactional
public class PeriodoServiceImpl implements PeriodoService {

    private final PeriodoRepository repository;
    private final AnioAcademicoRepository anioRepository;
    private final PlanificacionMapper mapper;
    private final ApplicationEventPublisher eventos;

    @Override
    @Transactional(readOnly = true)
    public List<PeriodoResponse> listar(Long anioId) {
        if (anioId == null) {
            return repository.findAll().stream().map(mapper::toResponse).toList();
        }
        return repository.findByAnioIdOrderByNumero(anioId).stream().map(mapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PeriodoResponse obtener(Long id) {
        return mapper.toResponse(buscar(id));
    }

    @Override
    public PeriodoResponse crear(PeriodoRequest request) {
        AnioAcademico anio = anioEditable(request.anioId());
        validar(request, anio, null);
        Periodo periodo = mapper.toEntity(request);
        periodo.setAnio(anio);
        periodo.setEstado(EstadoPeriodo.PLANIFICADO);
        return mapper.toResponse(repository.save(periodo));
    }

    @Override
    public PeriodoResponse actualizar(Long id, PeriodoRequest request) {
        Periodo periodo = buscar(id);
        if (!periodo.getAnio().getId().equals(request.anioId())) {
            throw new BusinessRuleException("No se puede mover un periodo a otro año académico");
        }
        AnioAcademico anio = anioEditable(request.anioId());
        if (periodo.getEstado() == EstadoPeriodo.CERRADO) {
            throw new BusinessRuleException("Un periodo CERRADO es de solo lectura");
        }
        validar(request, anio, id);
        mapper.update(request, periodo);
        return mapper.toResponse(repository.save(periodo));
    }

    @Override
    public PeriodoResponse cambiarEstado(Long id, EstadoPeriodo nuevo) {
        Periodo periodo = buscar(id);
        anioEditable(periodo.getAnio().getId());
        EstadoPeriodo actual = periodo.getEstado();
        if (actual == nuevo) {
            return mapper.toResponse(periodo);
        }
        if (nuevo.ordinal() < actual.ordinal()) {
            throw new BusinessRuleException("El estado de un periodo solo avanza: PLANIFICADO → ACTIVO → CERRADO");
        }
        if (nuevo == EstadoPeriodo.ACTIVO
                && repository.existsByAnioIdAndEstadoAndIdNot(periodo.getAnio().getId(), EstadoPeriodo.ACTIVO, id)) {
            throw new BusinessRuleException("Ya hay otro periodo ACTIVO en este año: ciérrelo primero");
        }
        periodo.setEstado(nuevo);
        repository.save(periodo);
        eventos.publishEvent(PeriodoCambiadoEvent.of(periodo.getId(), periodo.getNumero(), periodo.getNombre(),
                periodo.getEstado().name(), periodo.getAnio().getId()));
        return mapper.toResponse(periodo);
    }

    @Override
    public void eliminar(Long id) {
        Periodo periodo = buscar(id);
        if (periodo.getEstado() != EstadoPeriodo.PLANIFICADO) {
            throw new BusinessRuleException("Solo se elimina un periodo en estado PLANIFICADO");
        }
        repository.delete(periodo);
    }

    private void validar(PeriodoRequest r, AnioAcademico anio, Long excluirId) {
        if (!r.fechaFin().isAfter(r.fechaInicio())) {
            throw new BusinessRuleException("La fecha de fin debe ser posterior a la fecha de inicio");
        }
        if (r.fechaInicio().isBefore(anio.getFechaInicio()) || r.fechaFin().isAfter(anio.getFechaFin())) {
            throw new BusinessRuleException("El periodo debe estar dentro del rango del año académico ("
                    + anio.getFechaInicio() + " a " + anio.getFechaFin() + ")");
        }
        boolean numeroRepetido = excluirId == null
                ? repository.existsByAnioIdAndNumero(r.anioId(), r.numero())
                : repository.existsByAnioIdAndNumeroAndIdNot(r.anioId(), r.numero(), excluirId);
        if (numeroRepetido) {
            throw new BusinessRuleException("Ya existe el periodo número " + r.numero() + " en este año");
        }
        if (repository.haySolapamiento(r.anioId(), r.fechaInicio(), r.fechaFin(), excluirId)) {
            throw new BusinessRuleException("Las fechas se solapan con otro periodo del año");
        }
    }

    private AnioAcademico anioEditable(Long anioId) {
        AnioAcademico anio = anioRepository.findById(anioId)
                .orElseThrow(() -> ResourceNotFoundException.of("Año académico", anioId));
        if (anio.getEstado() == EstadoPeriodo.CERRADO) {
            throw new BusinessRuleException("El año " + anio.getNombre() + " está CERRADO (solo lectura)");
        }
        return anio;
    }

    private Periodo buscar(Long id) {
        return repository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Periodo", id));
    }
}
