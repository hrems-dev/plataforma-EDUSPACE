package pe.edu.upeu.eduspace.calificaciones.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upeu.eduspace.calificaciones.entity.MatriculaLocal;
import pe.edu.upeu.eduspace.calificaciones.event.MatriculaEvent;
import pe.edu.upeu.eduspace.calificaciones.repository.MatriculaLocalRepository;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatriculaLocalService {

    private final MatriculaLocalRepository matriculaLocalRepository;

    @Transactional
    public void sincronizarMatricula(MatriculaEvent evento) {
        if (evento == null || evento.getMatriculaId() == null) {
            return;
        }

        MatriculaLocal local = matriculaLocalRepository.findById(evento.getMatriculaId())
            .orElseGet(() -> MatriculaLocal.builder()
                .matriculaId(evento.getMatriculaId())
                .alumnoId(evento.getAlumnoId())
                .alumnoNombre(evento.getAlumnoNombre() != null ? evento.getAlumnoNombre() : "Alumno " + evento.getAlumnoId())
                .anioId(evento.getAnioId() != null ? evento.getAnioId() : 0L)
                .nivelId(evento.getNivelId())
                .gradoId(evento.getGradoId() != null ? evento.getGradoId() : 0L)
                .seccionId(evento.getSeccionId() != null ? evento.getSeccionId() : 0L)
                .estado(evento.getEstado() != null ? evento.getEstado() : "PRE_MATRICULA")
                .actualizadoEn(LocalDateTime.now())
                .build());

        if (evento.getAlumnoNombre() != null && !evento.getAlumnoNombre().isBlank()) {
            local.setAlumnoNombre(evento.getAlumnoNombre());
        }
        if (evento.getNivelId() != null) {
            local.setNivelId(evento.getNivelId());
        }
        if (evento.getGradoId() != null) {
            local.setGradoId(evento.getGradoId());
        }
        if (evento.getSeccionId() != null) {
            local.setSeccionId(evento.getSeccionId());
        }
        if (evento.getEstado() != null) {
            local.setEstado(evento.getEstado());
        }
        local.setActualizadoEn(LocalDateTime.now());

        matriculaLocalRepository.save(local);
        log.info("Matrícula sincronizada en réplica local: id={}, alumno={}, estado={}",
            local.getMatriculaId(), local.getAlumnoId(), local.getEstado());
    }
}

