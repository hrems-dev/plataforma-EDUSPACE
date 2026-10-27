package pe.edu.upeu.eduspace.examenes.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upeu.eduspace.examenes.entity.MatriculaLocal;
import pe.edu.upeu.eduspace.examenes.event.MatriculaEvent;
import pe.edu.upeu.eduspace.examenes.repository.MatriculaLocalRepository;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatriculaLocalService {

    private final MatriculaLocalRepository matriculaLocalRepository;

    @Transactional
    public MatriculaLocal sincronizarDesdeEvento(MatriculaEvent event) {
        MatriculaLocal ml = matriculaLocalRepository.findById(event.getMatriculaId())
            .orElseGet(() -> MatriculaLocal.builder()
                .matriculaId(event.getMatriculaId())
                .alumnoId(event.getAlumnoId())
                .anioId(event.getAnioId())
                .build());

        ml.setAlumnoNombre(event.getAlumnoNombre() != null ? event.getAlumnoNombre() : "Alumno " + event.getAlumnoId());
        ml.setNivelId(event.getNivelId());
        ml.setGradoId(event.getGradoId());
        ml.setSeccionId(event.getSeccionId());
        ml.setEstado(event.getEstado());
        ml.setActualizadoEn(LocalDateTime.now());

        log.info("Sincronizada matrícula local {} para alumno {} en examenes-ms", ml.getMatriculaId(), ml.getAlumnoId());
        return matriculaLocalRepository.save(ml);
    }
}

