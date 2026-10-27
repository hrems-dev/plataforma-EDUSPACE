package pe.edu.eduspace.alumnos.service.impl;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.eduspace.alumnos.config.ExpedienteProperties;
import pe.edu.eduspace.alumnos.dto.ExpedienteCompletoResponse;
import pe.edu.eduspace.alumnos.entity.TipoDocumento;
import pe.edu.eduspace.alumnos.exception.ResourceNotFoundException;
import pe.edu.eduspace.alumnos.repository.AlumnoDocumentoRepository;
import pe.edu.eduspace.alumnos.repository.AlumnoRepository;
import pe.edu.eduspace.alumnos.service.ExpedienteService;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExpedienteServiceImpl implements ExpedienteService {

    private final AlumnoRepository alumnoRepository;
    private final AlumnoDocumentoRepository documentoRepository;
    private final ExpedienteProperties props;

    @Override
    public ExpedienteCompletoResponse evaluar(Long alumnoId) {
        if (!alumnoRepository.existsById(alumnoId)) {
            throw ResourceNotFoundException.of("Alumno", alumnoId);
        }
        Set<TipoDocumento> verificados = documentoRepository.findByAlumnoIdOrderBySubidoEnDesc(alumnoId).stream()
                .filter(d -> Boolean.TRUE.equals(d.getVerificado())).map(d -> d.getTipo()).collect(Collectors.toSet());
        List<TipoDocumento> faltantes = props.requeridos().stream().filter(t -> !verificados.contains(t)).toList();
        return new ExpedienteCompletoResponse(faltantes.isEmpty(), faltantes);
    }
}
