package pe.edu.upeu.eduspace.asistencia.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pe.edu.upeu.eduspace.asistencia.repository.MatriculaLocalRepository;

@Component
@RequiredArgsConstructor
public class MatriculaNombres {

    private final MatriculaLocalRepository repo;

    public String de(Long matriculaId, Long alumnoId) {
        return repo.findById(matriculaId).map(m -> m.getAlumnoNombre()).orElse("Alumno " + alumnoId);
    }
}
