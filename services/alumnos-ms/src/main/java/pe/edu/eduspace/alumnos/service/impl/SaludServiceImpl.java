package pe.edu.eduspace.alumnos.service.impl;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.eduspace.alumnos.dto.SaludRequest;
import pe.edu.eduspace.alumnos.dto.SaludResponse;
import pe.edu.eduspace.alumnos.entity.Alumno;
import pe.edu.eduspace.alumnos.entity.AlumnoSalud;
import pe.edu.eduspace.alumnos.exception.ResourceNotFoundException;
import pe.edu.eduspace.alumnos.mapper.AlumnoMapper;
import pe.edu.eduspace.alumnos.repository.AlumnoRepository;
import pe.edu.eduspace.alumnos.repository.AlumnoSaludRepository;
import pe.edu.eduspace.alumnos.service.SaludService;
import pe.edu.eduspace.alumnos.util.UsuarioActual;

@Service
@RequiredArgsConstructor
@Transactional
public class SaludServiceImpl implements SaludService {

    private static final Logger log = LoggerFactory.getLogger(SaludServiceImpl.class);

    private final AlumnoSaludRepository repository;
    private final AlumnoRepository alumnoRepository;
    private final AlumnoMapper mapper;

    /**
     * El informe permite leer la ficha al "docente tutor", pero saber quién es tutor de qué alumno exige datos de
     * planificacion-ms y matricula-ms. Mientras no exista esa consulta, esta bandera decide si los DOCENTE leen.
     */
    @Value("${app.salud.docentes-pueden-leer:false}")
    private boolean docentesPuedenLeer;

    @Override
    @Transactional(readOnly = true)
    public SaludResponse obtener(Long alumnoId, Long usuarioId) {
        if (UsuarioActual.tieneAlgunRol("DOCENTE") && !UsuarioActual.tieneAlgunRol("ADMIN", "SECRETARIA") && !docentesPuedenLeer) {
            throw new AccessDeniedException("La ficha de salud está restringida a secretaría y administración");
        }
        AlumnoSalud salud = repository.findById(alumnoId).orElseThrow(() -> {
            if (!alumnoRepository.existsById(alumnoId)) {
                return ResourceNotFoundException.of("Alumno", alumnoId);
            }
            return new ResourceNotFoundException("El alumno " + alumnoId + " aún no tiene ficha de salud");
        });
        log.info("component=SaludService eventType=ConsultaFichaSalud key={} status=OK usuarioId={}", alumnoId, usuarioId);
        return mapper.toResponse(salud);
    }

    @Override
    public SaludResponse guardar(Long alumnoId, SaludRequest request, Long usuarioId) {
        Alumno alumno = alumnoRepository.findById(alumnoId).orElseThrow(() -> ResourceNotFoundException.of("Alumno", alumnoId));
        AlumnoSalud salud = repository.findById(alumnoId).orElseGet(() -> {
            AlumnoSalud nueva = new AlumnoSalud();
            nueva.setAlumno(alumno);
            return nueva;
        });
        mapper.update(request, salud);
        AlumnoSalud guardada = repository.save(salud);
        log.info("component=SaludService eventType=ActualizacionFichaSalud key={} status=OK usuarioId={}", alumnoId, usuarioId);
        return mapper.toResponse(guardada);
    }
}
