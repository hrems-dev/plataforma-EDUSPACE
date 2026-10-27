package pe.edu.upeu.eduspace.examenes.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upeu.eduspace.examenes.dto.IntentoResultadoResponse;
import pe.edu.upeu.eduspace.examenes.entity.*;
import pe.edu.upeu.eduspace.examenes.event.ExamenCalificadoEvent;
import pe.edu.upeu.eduspace.examenes.exception.ResourceNotFoundException;
import pe.edu.upeu.eduspace.examenes.messaging.ExamenEventProducer;
import pe.edu.upeu.eduspace.examenes.repository.ExamenPreguntaRepository;
import pe.edu.upeu.eduspace.examenes.repository.IntentoRepository;
import pe.edu.upeu.eduspace.examenes.repository.RespuestaRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class CalificacionAutomaticaService {

    private final IntentoRepository intentoRepository;
    private final RespuestaRepository respuestaRepository;
    private final ExamenPreguntaRepository examenPreguntaRepository;
    private final ExamenEventProducer examenEventProducer;

    @Transactional
    public IntentoResultadoResponse calificarIntento(Long intentoId) {
        Intento intento = intentoRepository.findById(intentoId)
            .orElseThrow(() -> new ResourceNotFoundException("Intento no encontrado: " + intentoId));

        Examen examen = intento.getExamen();
        List<ExamenPregunta> examenPreguntas = examenPreguntaRepository.findByExamenIdOrderByOrdenAsc(examen.getId());
        Map<Long, BigDecimal> puntajesPorPregunta = new HashMap<>();
        for (ExamenPregunta ep : examenPreguntas) {
            puntajesPorPregunta.put(ep.getPregunta().getId(), ep.getPuntaje());
        }

        List<Respuesta> respuestas = respuestaRepository.findByIntentoId(intentoId);
        Map<Long, Respuesta> mapRespuestas = new HashMap<>();
        for (Respuesta r : respuestas) {
            mapRespuestas.put(r.getPregunta().getId(), r);
        }

        BigDecimal sumaPuntaje = BigDecimal.ZERO;
        boolean tieneAbiertasPendientes = false;

        for (ExamenPregunta ep : examenPreguntas) {
            Pregunta p = ep.getPregunta();
            BigDecimal puntajeMaximoPregunta = ep.getPuntaje();
            Respuesta r = mapRespuestas.get(p.getId());

            if (r == null) {
                // No respondió esta pregunta
                continue;
            }

            if ("ABIERTA".equals(p.getTipo())) {
                r.setRequiereRevision(true);
                tieneAbiertasPendientes = true;
                respuestaRepository.save(r);
                continue;
            }

            // Calificación de preguntas objetivas
            BigDecimal puntajeGanado = evaluarPreguntaObjetiva(p, r, puntajeMaximoPregunta);
            r.setPuntajeObtenido(puntajeGanado);
            r.setRequiereRevision(false);
            respuestaRepository.save(r);

            sumaPuntaje = sumaPuntaje.add(puntajeGanado);
        }

        intento.setPuntajeObtenido(sumaPuntaje);

        if (!tieneAbiertasPendientes) {
            // Se calcula nota oficial 0-20: (puntajeObtenido / puntajeTotal) * 20
            BigDecimal nota = BigDecimal.ZERO;
            if (examen.getPuntajeTotal().compareTo(BigDecimal.ZERO) > 0) {
                nota = sumaPuntaje.multiply(BigDecimal.valueOf(20))
                    .divide(examen.getPuntajeTotal(), 2, RoundingMode.HALF_UP)
                    .min(BigDecimal.valueOf(20))
                    .max(BigDecimal.ZERO);
            }
            intento.setNota(nota);
            intento.setEstado("CALIFICADO");
            intento.setCalificadoEn(LocalDateTime.now());
            intentoRepository.save(intento);

            // Publicar evento examen.calificado
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

            log.info("Intento {} calificado automáticamente con nota {}", intentoId, nota);
        } else {
            intentoRepository.save(intento);
            log.info("Intento {} guardado con calificación parcial; requiere revisión de preguntas abiertas.", intentoId);
        }

        return IntentoResultadoResponse.builder()
            .intentoId(intento.getId())
            .examenId(examen.getId())
            .tituloExamen(examen.getTitulo())
            .numeroIntento(intento.getNumero())
            .estado(intento.getEstado())
            .puntajeObtenido(intento.getPuntajeObtenido())
            .puntajeTotal(examen.getPuntajeTotal())
            .nota(intento.getNota())
            .iniciadoEn(intento.getIniciadoEn())
            .enviadoEn(intento.getEnviadoEn())
            .calificadoEn(intento.getCalificadoEn())
            .requiereRevisionAbiertas(tieneAbiertasPendientes)
            .build();
    }

    private BigDecimal evaluarPreguntaObjetiva(Pregunta p, Respuesta r, BigDecimal puntajeMaximo) {
        if (r.getOpcionesIds() == null || r.getOpcionesIds().isEmpty()) {
            return BigDecimal.ZERO;
        }

        Set<Long> seleccionadas = new HashSet<>(r.getOpcionesIds());
        Set<Long> correctas = new HashSet<>();
        for (OpcionPregunta op : p.getOpciones()) {
            if (Boolean.TRUE.equals(op.getEsCorrecta())) {
                correctas.add(op.getId());
            }
        }

        if ("OPCION_UNICA".equals(p.getTipo()) || "VERDADERO_FALSO".equals(p.getTipo())) {
            return seleccionadas.equals(correctas) ? puntajeMaximo : BigDecimal.ZERO;
        }

        if ("OPCION_MULTIPLE".equals(p.getTipo())) {
            // Todo o nada para consistencia segura
            return seleccionadas.equals(correctas) ? puntajeMaximo : BigDecimal.ZERO;
        }

        return BigDecimal.ZERO;
    }
}

