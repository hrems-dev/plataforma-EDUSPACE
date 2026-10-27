package pe.edu.eduspace.alumnos.service.impl;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.eduspace.alumnos.dto.*;
import pe.edu.eduspace.alumnos.exception.ResourceNotFoundException;
import pe.edu.eduspace.alumnos.mapper.AlumnoMapper;
import pe.edu.eduspace.alumnos.repository.AlumnoApoderadoRepository;
import pe.edu.eduspace.alumnos.repository.AlumnoRepository;
import pe.edu.eduspace.alumnos.service.InternalConsultaService;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InternalConsultaServiceImpl implements InternalConsultaService {

    private final AlumnoRepository alumnoRepository;
    private final AlumnoApoderadoRepository vinculoRepository;
    private final AlumnoMapper mapper;

    @Override
    public AlumnoBasicoResponse basico(Long alumnoId) {
        return alumnoRepository.findById(alumnoId).map(mapper::toBasico)
                .orElseThrow(() -> ResourceNotFoundException.of("Alumno", alumnoId));
    }

    @Override
    public AlumnoUsuarioResponse alumnoDeUsuario(Long usuarioId) {
        return alumnoRepository.findByUsuarioId(usuarioId).map(a -> new AlumnoUsuarioResponse(a.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("El usuario " + usuarioId + " no es un alumno"));
    }

    @Override
    public List<HijoResponse> hijosDeUsuario(Long usuarioId) {
        return vinculoRepository.findByUsuarioDelApoderado(usuarioId).stream().map(mapper::toHijo).toList();
    }

    @Override
    public List<AlumnoBasicoResponse> lote(List<Long> ids) {
        return alumnoRepository.findByIdIn(ids).stream().map(mapper::toBasico).toList();
    }

    @Override
    public ResponsablePagoResponse responsablePago(Long alumnoId) {
        if (!alumnoRepository.existsById(alumnoId)) {
            throw ResourceNotFoundException.of("Alumno", alumnoId);
        }
        return vinculoRepository.findResponsablePago(alumnoId)
                .map(v -> new ResponsablePagoResponse(v.getApoderado().getId(), v.getApoderado().getUsuarioId(),
                        v.getApoderado().nombreCompleto()))
                .orElseThrow(() -> new ResourceNotFoundException("El alumno " + alumnoId + " no tiene responsable de pago"));
    }
}
