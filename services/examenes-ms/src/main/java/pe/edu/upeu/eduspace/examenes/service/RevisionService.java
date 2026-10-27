package pe.edu.upeu.eduspace.examenes.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upeu.eduspace.examenes.dto.RevisionRequest;
import pe.edu.upeu.eduspace.examenes.entity.Examen;
import pe.edu.upeu.eduspace.examenes.entity.Intento;
import pe.edu.upeu.eduspace.examenes.entity.Respuesta;
import pe.edu.upeu.eduspace.examenes.event.ExamenCalificadoEvent;
import pe.edu.upeu.eduspace.examenes.exception.ResourceNotFoundException;
import pe.edu.upeu.eduspace.examenes.messaging.ExamenEventProducer;
import pe.edu.upeu.eduspace.examenes.repository.IntentoRepository;
import pe.edu.upeu.eduspace.examenes.repository.RespuestaRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RevisionService {

    private final RespuestaRepository respuestaRepository;
    private final IntentoRepository intentoRepository;
    private final ExamenEventProducer examenEventProducer;

    @Transactional
    public void revisarRespuestaAbierta(Long respuestaId, RevisionRequest req, Long docenteId) {
        Respuesta r = respuestaRepository.findById(respuestaId)
            .orElseThrow(() -> new ResourceNotFoundException("Respuesta no encontrada: " + respuestaId));

        r.setPuntajeObtenido(req.getPuntajeObtenido());
        r.setRequiereRevision(false);
        r.setRevisadaPor(docenteId);
        respuestaRepository.save(r);

        Intento intento = r.getIntento();
        List<Respuesta> todasRespuestas = respuestaRepository.findByIntentoId(intento.getId());
        boolean quedanPendientes = todasRespuestas.stream().anyMatch(resp -> Boolean.TRUE.equals(resp.getRequiereRevision()));

        if (!quedanPendientes) {
            BigDecimal sumaTotal = todasRespuestas.stream()
                .map(resp -> resp.getPuntajeObtenido() != null ? resp.getPuntajeObtenido() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            Examen examen = intento.getExamen();
            BigDecimal nota = BigDecimal.ZERO;
            if (examen.getPuntajeTotal().compareTo(BigDecimal.ZERO) > 0) {
                nota = sumaTotal.multiply(BigDecimal.valueOf(20))
                    .divide(examen.getPuntajeTotal(), 2, RoundingMode.HALF_UP)
                    .min(BigDecimal.valueOf(20))
                    .max(BigDecimal.ZERO);
            }

            intento.setPuntajeObtenido(sumaTotal);
            intento.setNota(nota);
            intento.setEstado("CALIFICADO");
            intento.setCalificadoEn(LocalDateTime.now());
            intentoRepository.save(intento);

            // Emitir evento examen.calificado actualizado
            examenEventProducer.publicarExamenCalificado(ExamenCalificadoEvent.builder()
                .examenId(examen.getId())
                .intentoId(intento.getId())
                .alumnoId(intento.getAlumnoId())
                .matriculaId(intento.getMatriculaId())
                .seccionId(examen.getSeccionId())
                .cursoId(examen.getCursoId())
                .periodoId(examen.getPeriodoId())
                .titulo(examen.getTitulo())
                .nota(nota)
                .build());

            log.info("Revisión completada para intento {}. Nota final: {}", intento.getId(), nota);
        }
    }
}

