package pe.edu.eduspace.planificacion.service.impl;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.eduspace.planificacion.dto.BloqueRequest;
import pe.edu.eduspace.planificacion.dto.BloqueResponse;
import pe.edu.eduspace.planificacion.entity.BloqueHorario;
import pe.edu.eduspace.planificacion.entity.Turno;
import pe.edu.eduspace.planificacion.exception.BusinessRuleException;
import pe.edu.eduspace.planificacion.exception.ResourceNotFoundException;
import pe.edu.eduspace.planificacion.mapper.PlanificacionMapper;
import pe.edu.eduspace.planificacion.repository.BloqueHorarioRepository;
import pe.edu.eduspace.planificacion.repository.HorarioRepository;
import pe.edu.eduspace.planificacion.service.BloqueHorarioService;

@Service
@RequiredArgsConstructor
@Transactional
public class BloqueHorarioServiceImpl implements BloqueHorarioService {

    private final BloqueHorarioRepository repository;
    private final HorarioRepository horarioRepository;
    private final PlanificacionMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public List<BloqueResponse> listar(Turno turno) {
        List<BloqueHorario> bloques = turno == null ? repository.findAllByOrderByTurnoAscOrdenAsc() : repository.findByTurnoOrderByOrden(turno);
        return bloques.stream().map(mapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public BloqueResponse obtener(Long id) {
        return mapper.toResponse(buscar(id));
    }

    @Override
    public BloqueResponse crear(BloqueRequest request) {
        validar(request, null);
        BloqueHorario bloque = mapper.toEntity(request);
        bloque.setEsReceso(Boolean.TRUE.equals(request.esReceso()));
        return mapper.toResponse(repository.save(bloque));
    }

    @Override
    public BloqueResponse actualizar(Long id, BloqueRequest request) {
        BloqueHorario bloque = buscar(id);
        validar(request, id);
        if ((bloque.getTurno() != request.turno() || Boolean.TRUE.equals(request.esReceso()))
                && horarioRepository.existsByBloqueId(id)) {
            throw new BusinessRuleException("El bloque ya tiene clases programadas: no se puede cambiar su turno ni convertirlo en receso");
        }
        mapper.update(request, bloque);
        bloque.setEsReceso(Boolean.TRUE.equals(request.esReceso()));
        return mapper.toResponse(repository.save(bloque));
    }

    @Override
    public void eliminar(Long id) {
        BloqueHorario bloque = buscar(id);
        if (horarioRepository.existsByBloqueId(id)) {
            throw new BusinessRuleException("El bloque tiene clases programadas en el horario");
        }
        repository.delete(bloque);
    }

    private void validar(BloqueRequest r, Long excluirId) {
        if (!r.horaFin().isAfter(r.horaInicio())) {
            throw new BusinessRuleException("La hora de fin debe ser posterior a la hora de inicio");
        }
        boolean ordenRepetido = excluirId == null
                ? repository.existsByTurnoAndOrden(r.turno(), r.orden())
                : repository.existsByTurnoAndOrdenAndIdNot(r.turno(), r.orden(), excluirId);
        if (ordenRepetido) {
            throw new BusinessRuleException("Ya existe el bloque " + r.orden() + " en el turno " + r.turno());
        }
        if (repository.haySolapamiento(r.turno(), r.horaInicio(), r.horaFin(), excluirId)) {
            throw new BusinessRuleException("El bloque se solapa con otro del turno " + r.turno());
        }
    }

    private BloqueHorario buscar(Long id) {
        return repository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Bloque horario", id));
    }
}
