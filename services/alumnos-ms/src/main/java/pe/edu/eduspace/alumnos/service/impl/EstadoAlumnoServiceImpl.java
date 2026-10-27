package pe.edu.eduspace.alumnos.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.eduspace.alumnos.dto.AlumnoResumenResponse;
import pe.edu.eduspace.alumnos.dto.CambioEstadoRequest;
import pe.edu.eduspace.alumnos.dto.HistorialResponse;
import pe.edu.eduspace.alumnos.entity.Alumno;
import pe.edu.eduspace.alumnos.entity.AlumnoEstadoHistorial;
import pe.edu.eduspace.alumnos.event.AlumnoEstadoCambiadoEvent;
import pe.edu.eduspace.alumnos.exception.BusinessRuleException;
import pe.edu.eduspace.alumnos.exception.ResourceNotFoundException;
import pe.edu.eduspace.alumnos.mapper.AlumnoMapper;
import pe.edu.eduspace.alumnos.repository.AlumnoEstadoHistorialRepository;
import pe.edu.eduspace.alumnos.repository.AlumnoRepository;
import pe.edu.eduspace.alumnos.service.EstadoAlumnoService;

/** Un alumno nunca se borra: cambia de estado y queda el historial con el motivo y quién lo hizo. */
@Service
@RequiredArgsConstructor
@Transactional
public class EstadoAlumnoServiceImpl implements EstadoAlumnoService {

    private final AlumnoRepository repository;
    private final AlumnoEstadoHistorialRepository historialRepository;
    private final AlumnoMapper mapper;
    private final ApplicationEventPublisher eventos;

    @Override
    public AlumnoResumenResponse cambiar(Long alumnoId, CambioEstadoRequest request, Long usuarioId) {
        Alumno alumno = repository.findById(alumnoId).orElseThrow(() -> ResourceNotFoundException.of("Alumno", alumnoId));
        if (alumno.getEstado() == request.estado()) {
            throw new BusinessRuleException("El alumno ya está en estado " + request.estado());
        }
        LocalDateTime ahora = LocalDateTime.now();
        AlumnoEstadoHistorial historial = new AlumnoEstadoHistorial();
        historial.setAlumno(alumno);
        historial.setEstadoAnterior(alumno.getEstado());
        historial.setEstadoNuevo(request.estado());
        historial.setMotivo(request.motivo());
        historial.setUsuarioId(usuarioId);
        historial.setCambiadoEn(ahora);
        historialRepository.save(historial);

        String anterior = alumno.getEstado().name();
        alumno.setEstado(request.estado());
        repository.save(alumno);
        eventos.publishEvent(AlumnoEstadoCambiadoEvent.of(alumnoId, anterior, request.estado().name(), request.motivo(), ahora));
        return mapper.toResumen(alumno);
    }

    @Override
    @Transactional(readOnly = true)
    public List<HistorialResponse> historial(Long alumnoId) {
        if (!repository.existsById(alumnoId)) {
            throw ResourceNotFoundException.of("Alumno", alumnoId);
        }
        return historialRepository.findByAlumnoIdOrderByCambiadoEnDescIdDesc(alumnoId).stream().map(mapper::toResponse).toList();
    }
}
