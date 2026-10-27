package pe.edu.eduspace.alumnos.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.eduspace.alumnos.entity.Alumno;
import pe.edu.eduspace.alumnos.entity.Apoderado;
import pe.edu.eduspace.alumnos.exception.ResourceNotFoundException;
import pe.edu.eduspace.alumnos.repository.AlumnoApoderadoRepository;
import pe.edu.eduspace.alumnos.repository.AlumnoRepository;
import pe.edu.eduspace.alumnos.repository.ApoderadoRepository;
import pe.edu.eduspace.alumnos.service.AccesoService;
import pe.edu.eduspace.alumnos.util.UsuarioActual;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccesoServiceImpl implements AccesoService {

    private static final String[] PERSONAL = {"ADMIN", "SECRETARIA", "DOCENTE"};

    private final AlumnoRepository alumnoRepository;
    private final ApoderadoRepository apoderadoRepository;
    private final AlumnoApoderadoRepository vinculoRepository;

    @Override
    public void verificarLecturaAlumno(Long alumnoId) {
        Alumno alumno = alumnoRepository.findById(alumnoId)
                .orElseThrow(() -> ResourceNotFoundException.of("Alumno", alumnoId));
        if (UsuarioActual.tieneAlgunRol(PERSONAL)) {
            return;
        }
        Long yo = UsuarioActual.id();
        if (yo == null) {
            throw new AccessDeniedException("Sin usuario en el token");
        }
        boolean esElAlumno = UsuarioActual.tieneAlgunRol("ALUMNO") && yo.equals(alumno.getUsuarioId());
        boolean esApoderado = UsuarioActual.tieneAlgunRol("APODERADO")
                && vinculoRepository.existsByAlumnoIdAndApoderadoUsuarioId(alumnoId, yo);
        if (!esElAlumno && !esApoderado) {
            throw new AccessDeniedException("El alumno " + alumnoId + " no es suyo");
        }
    }

    @Override
    public void verificarLecturaApoderado(Long apoderadoId) {
        Apoderado apoderado = apoderadoRepository.findById(apoderadoId)
                .orElseThrow(() -> ResourceNotFoundException.of("Apoderado", apoderadoId));
        if (UsuarioActual.tieneAlgunRol("ADMIN", "SECRETARIA")) {
            return;
        }
        Long yo = UsuarioActual.id();
        if (yo == null || !UsuarioActual.tieneAlgunRol("APODERADO") || !yo.equals(apoderado.getUsuarioId())) {
            throw new AccessDeniedException("Solo puede ver sus propios datos");
        }
    }
}
