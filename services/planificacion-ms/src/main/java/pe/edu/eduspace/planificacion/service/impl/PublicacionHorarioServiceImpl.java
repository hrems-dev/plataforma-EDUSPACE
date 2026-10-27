package pe.edu.eduspace.planificacion.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.eduspace.planificacion.dto.PublicacionResponse;
import pe.edu.eduspace.planificacion.dto.ResultadoValidacionHorario;
import pe.edu.eduspace.planificacion.entity.EstadoPeriodo;
import pe.edu.eduspace.planificacion.entity.Horario;
import pe.edu.eduspace.planificacion.entity.PublicacionHorario;
import pe.edu.eduspace.planificacion.entity.Seccion;
import pe.edu.eduspace.planificacion.event.HorarioPublicadoEvent;
import pe.edu.eduspace.planificacion.exception.BusinessRuleException;
import pe.edu.eduspace.planificacion.exception.ResourceNotFoundException;
import pe.edu.eduspace.planificacion.mapper.PlanificacionMapper;
import pe.edu.eduspace.planificacion.repository.HorarioRepository;
import pe.edu.eduspace.planificacion.repository.PublicacionHorarioRepository;
import pe.edu.eduspace.planificacion.repository.SeccionRepository;
import pe.edu.eduspace.planificacion.service.HorarioService;
import pe.edu.eduspace.planificacion.service.PublicacionHorarioService;

@Service
@RequiredArgsConstructor
@Transactional
public class PublicacionHorarioServiceImpl implements PublicacionHorarioService {

    private final PublicacionHorarioRepository repository;
    private final SeccionRepository seccionRepository;
    private final HorarioRepository horarioRepository;
    private final HorarioService horarioService;
    private final PlanificacionMapper mapper;
    private final ApplicationEventPublisher eventos;

    @Override
    public PublicacionResponse publicar(Long seccionId, Long usuarioId) {
        Seccion seccion = seccionRepository.findById(seccionId)
                .orElseThrow(() -> ResourceNotFoundException.of("Sección", seccionId));
        if (seccion.getAnio().getEstado() == EstadoPeriodo.CERRADO) {
            throw new BusinessRuleException("El año " + seccion.getAnio().getNombre() + " está CERRADO (solo lectura)");
        }
        List<Horario> clases = horarioRepository.findBySeccion(seccionId);
        if (clases.isEmpty()) {
            throw new BusinessRuleException("La sección no tiene clases programadas: no hay nada que publicar");
        }
        ResultadoValidacionHorario validacion = horarioService.validar(seccionId);
        if (!validacion.choques().isEmpty()) {
            throw new BusinessRuleException("El horario tiene choques y no se puede publicar: "
                    + String.join("; ", validacion.choques()));
        }
        int version = repository.findFirstBySeccionIdOrderByVersionDesc(seccionId).map(p -> p.getVersion() + 1).orElse(1);
        PublicacionHorario publicacion = new PublicacionHorario();
        publicacion.setSeccion(seccion);
        publicacion.setVersion(version);
        publicacion.setPublicadoPor(usuarioId);
        publicacion.setPublicadoEn(LocalDateTime.now());
        repository.saveAndFlush(publicacion);

        List<Long> docenteUsuarioIds = clases.stream()
                .map(h -> h.getAsignacion().getDocente().getUsuarioId())
                .filter(u -> u != null).distinct().toList();
        eventos.publishEvent(HorarioPublicadoEvent.of(seccionId,
                seccion.getGrado().getNombre() + " " + seccion.getNombre(), seccion.getAnio().getId(), version,
                docenteUsuarioIds, publicacion.getPublicadoEn()));
        return mapper.toResponse(publicacion);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PublicacionResponse> historial(Long seccionId) {
        return repository.findBySeccionIdOrderByVersionDesc(seccionId).stream().map(mapper::toResponse).toList();
    }
}
