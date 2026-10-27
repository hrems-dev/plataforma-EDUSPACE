package pe.edu.upeu.eduspace.examenes.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upeu.eduspace.examenes.config.ExamenProperties;
import pe.edu.upeu.eduspace.examenes.dto.GuardarRespuestaRequest;
import pe.edu.upeu.eduspace.examenes.entity.Intento;
import pe.edu.upeu.eduspace.examenes.entity.Pregunta;
import pe.edu.upeu.eduspace.examenes.entity.Respuesta;
import pe.edu.upeu.eduspace.examenes.exception.BusinessRuleException;
import pe.edu.upeu.eduspace.examenes.exception.ResourceNotFoundException;
import pe.edu.upeu.eduspace.examenes.repository.ExamenPreguntaRepository;
import pe.edu.upeu.eduspace.examenes.repository.IntentoRepository;
import pe.edu.upeu.eduspace.examenes.repository.PreguntaRepository;
import pe.edu.upeu.eduspace.examenes.repository.RespuestaRepository;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class RespuestaService {

    private final RespuestaRepository respuestaRepository;
    private final IntentoRepository intentoRepository;
    private final PreguntaRepository preguntaRepository;
    private final ExamenProperties examenProperties;
    private final ExamenPreguntaRepository examenPreguntaRepository;

    @Transactional
    public void guardarRespuestaIncremental(Long intentoId, Long preguntaId, GuardarRespuestaRequest req, Long alumnoId) {
        Intento intento = intentoRepository.findById(intentoId)
            .orElseThrow(() -> new ResourceNotFoundException("Intento no encontrado: " + intentoId));

        if (!intento.getAlumnoId().equals(alumnoId)) {
            throw new BusinessRuleException("No está autorizado para responder en este intento.");
        }

        if (!"EN_CURSO".equals(intento.getEstado())) {
            throw new BusinessRuleException("El intento no se encuentra en curso (estado: " + intento.getEstado() + ").");
        }

        LocalDateTime ahora = LocalDateTime.now();
        LocalDateTime limiteConGracia = intento.getVenceEn().plusSeconds(examenProperties.getMargenGraciaSegundos());
        if (ahora.isAfter(limiteConGracia)) {
            throw new BusinessRuleException("El tiempo límite para rendir este examen ha expirado.");
        }

        Pregunta pregunta = preguntaRepository.findById(preguntaId)
            .orElseThrow(() -> new ResourceNotFoundException("Pregunta no encontrada: " + preguntaId));

        if (examenPreguntaRepository.findByExamenIdOrderByOrdenAsc(intento.getExamen().getId()).stream()
                .noneMatch(ep -> ep.getPregunta().getId().equals(preguntaId))) {
            throw new BusinessRuleException("La pregunta " + preguntaId + " no pertenece a este examen.");
        }

        // UPSERT incremental
        Respuesta r = respuestaRepository.findByIntentoIdAndPreguntaId(intentoId, preguntaId)
            .orElseGet(() -> Respuesta.builder()
                .intento(intento)
                .pregunta(pregunta)
                .requiereRevision("ABIERTA".equals(pregunta.getTipo()))
                .build());

        r.setOpcionesIds(req.getOpcionesIds());
        r.setTextoRespuesta(req.getTextoRespuesta());
        r.setGuardadaEn(ahora);

        respuestaRepository.save(r);
        log.debug("Respuesta incremental guardada para intento {} pregunta {}", intentoId, preguntaId);
    }
}

