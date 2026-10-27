package pe.edu.eduspace.planificacion.service.impl;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.eduspace.planificacion.dto.EsHabilResponse;
import pe.edu.eduspace.planificacion.dto.FeriadoRequest;
import pe.edu.eduspace.planificacion.dto.FeriadoResponse;
import pe.edu.eduspace.planificacion.entity.AnioAcademico;
import pe.edu.eduspace.planificacion.entity.EstadoPeriodo;
import pe.edu.eduspace.planificacion.entity.Feriado;
import pe.edu.eduspace.planificacion.exception.BusinessRuleException;
import pe.edu.eduspace.planificacion.exception.ResourceNotFoundException;
import pe.edu.eduspace.planificacion.mapper.PlanificacionMapper;
import pe.edu.eduspace.planificacion.repository.AnioAcademicoRepository;
import pe.edu.eduspace.planificacion.repository.FeriadoRepository;
import pe.edu.eduspace.planificacion.service.FeriadoService;

@Service
@RequiredArgsConstructor
@Transactional
public class FeriadoServiceImpl implements FeriadoService {

    private final FeriadoRepository repository;
    private final AnioAcademicoRepository anioRepository;
    private final PlanificacionMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public List<FeriadoResponse> listar(Long anioId) {
        List<Feriado> feriados = anioId == null ? repository.findAll() : repository.findByAnioIdOrderByFecha(anioId);
        return feriados.stream().map(mapper::toResponse).toList();
    }

    @Override
    public FeriadoResponse crear(FeriadoRequest request) {
        Feriado feriado = new Feriado();
        aplicar(feriado, request, true);
        return mapper.toResponse(repository.save(feriado));
    }

    @Override
    public FeriadoResponse actualizar(Long id, FeriadoRequest request) {
        Feriado feriado = buscar(id);
        aplicar(feriado, request, !feriado.getFecha().equals(request.fecha()) || !feriado.getAnio().getId().equals(request.anioId()));
        return mapper.toResponse(repository.save(feriado));
    }

    @Override
    public void eliminar(Long id) {
        repository.delete(buscar(id));
    }

    @Override
    @Transactional(readOnly = true)
    public EsHabilResponse esHabil(LocalDate fecha) {
        DayOfWeek dia = fecha.getDayOfWeek();
        if (dia == DayOfWeek.SATURDAY || dia == DayOfWeek.SUNDAY) {
            return new EsHabilResponse(fecha, false, "Fin de semana");
        }
        return repository.findFirstByFecha(fecha)
                .map(f -> new EsHabilResponse(fecha, false, f.getDescripcion()))
                .orElse(new EsHabilResponse(fecha, true, null));
    }

    private void aplicar(Feriado feriado, FeriadoRequest r, boolean validarDuplicado) {
        AnioAcademico anio = anioRepository.findById(r.anioId())
                .orElseThrow(() -> ResourceNotFoundException.of("Año académico", r.anioId()));
        if (anio.getEstado() == EstadoPeriodo.CERRADO) {
            throw new BusinessRuleException("El año " + anio.getNombre() + " está CERRADO (solo lectura)");
        }
        if (r.fecha().isBefore(anio.getFechaInicio()) || r.fecha().isAfter(anio.getFechaFin())) {
            throw new BusinessRuleException("El feriado debe estar dentro del año académico ("
                    + anio.getFechaInicio() + " a " + anio.getFechaFin() + ")");
        }
        if (validarDuplicado && repository.existsByAnioIdAndFecha(r.anioId(), r.fecha())) {
            throw new BusinessRuleException("Ya hay un feriado registrado el " + r.fecha());
        }
        feriado.setAnio(anio);
        feriado.setFecha(r.fecha());
        feriado.setDescripcion(r.descripcion());
    }

    private Feriado buscar(Long id) {
        return repository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Feriado", id));
    }
}
