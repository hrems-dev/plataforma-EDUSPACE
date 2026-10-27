package pe.edu.upeu.eduspace.examenes.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upeu.eduspace.examenes.dto.*;
import pe.edu.upeu.eduspace.examenes.entity.OpcionPregunta;
import pe.edu.upeu.eduspace.examenes.entity.Pregunta;
import pe.edu.upeu.eduspace.examenes.exception.BusinessRuleException;
import pe.edu.upeu.eduspace.examenes.exception.ResourceNotFoundException;
import pe.edu.upeu.eduspace.examenes.repository.OpcionPreguntaRepository;
import pe.edu.upeu.eduspace.examenes.repository.PreguntaRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PreguntaService {

    private final PreguntaRepository preguntaRepository;
    private final OpcionPreguntaRepository opcionPreguntaRepository;

    @Transactional(readOnly = true)
    public PageResponse<PreguntaResponse> listarBanco(Long cursoId, Long docenteId, String tema, String tipo, Pageable pageable) {
        Page<Pregunta> page = preguntaRepository.findBancoPreguntas(cursoId, docenteId, tema == null ? "" : tema.trim(), tipo == null || tipo.isBlank() ? null : tipo, pageable);
        return PageResponse.from(page.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public PreguntaResponse obtenerPorId(Long id) {
        Pregunta p = preguntaRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Pregunta no encontrada con ID: " + id));
        return toResponse(p);
    }

    @Transactional
    public PreguntaResponse crear(PreguntaRequest req, Long docenteId) {
        validarReglasPregunta(req.getTipo(), req.getOpciones());

        Pregunta p = Pregunta.builder()
            .cursoId(req.getCursoId())
            .docenteId(docenteId)
            .tema(req.getTema())
            .tipo(req.getTipo())
            .enunciado(req.getEnunciado())
            .puntaje(req.getPuntaje())
            .activa(req.getActiva() != null ? req.getActiva() : true)
            .creadaEn(LocalDateTime.now())
            .build();

        Pregunta guardada = preguntaRepository.save(p);

        if (req.getOpciones() != null && !req.getOpciones().isEmpty()) {
            List<OpcionPregunta> opciones = new ArrayList<>();
            short orden = 1;
            for (OpcionRequest opReq : req.getOpciones()) {
                OpcionPregunta op = OpcionPregunta.builder()
                    .pregunta(guardada)
                    .texto(opReq.getTexto())
                    .esCorrecta(opReq.getEsCorrecta() != null ? opReq.getEsCorrecta() : false)
                    .orden(opReq.getOrden() != null ? opReq.getOrden() : orden++)
                    .build();
                opciones.add(opcionPreguntaRepository.save(op));
            }
            guardada.setOpciones(opciones);
        }

        return toResponse(guardada);
    }

    @Transactional
    public PreguntaResponse actualizar(Long id, PreguntaRequest req, Long docenteId) {
        Pregunta p = preguntaRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Pregunta no encontrada con ID: " + id));
        verificarDuenio(p, docenteId);

        // Regla: no se edita si ya se usó en un examen publicado
        if (preguntaRepository.isPreguntaEnUsoEnExamenPublicado(id)) {
            throw new BusinessRuleException("No se puede modificar una pregunta ya utilizada en un examen publicado. Debe duplicarla o crear una nueva.");
        }

        validarReglasPregunta(req.getTipo(), req.getOpciones());

        p.setTema(req.getTema());
        p.setTipo(req.getTipo());
        p.setEnunciado(req.getEnunciado());
        p.setPuntaje(req.getPuntaje());
        if (req.getActiva() != null) {
            p.setActiva(req.getActiva());
        }

        p.getOpciones().clear();
        if (req.getOpciones() != null) {
            short orden = 1;
            for (OpcionRequest opReq : req.getOpciones()) {
                OpcionPregunta op = OpcionPregunta.builder()
                    .pregunta(p)
                    .texto(opReq.getTexto())
                    .esCorrecta(opReq.getEsCorrecta() != null ? opReq.getEsCorrecta() : false)
                    .orden(opReq.getOrden() != null ? opReq.getOrden() : orden++)
                    .build();
                p.getOpciones().add(op);
            }
        }

        return toResponse(preguntaRepository.save(p));
    }

    @Transactional
    public void eliminar(Long id, Long docenteId) {
        Pregunta p = preguntaRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Pregunta no encontrada con ID: " + id));
        verificarDuenio(p, docenteId);
        if (preguntaRepository.isPreguntaEnUsoEnExamenPublicado(id)) {
            p.setActiva(false);
            preguntaRepository.save(p);
            log.info("Pregunta {} desactivada lógicamente por estar en exámenes publicados.", id);
            return;
        }
        preguntaRepository.deleteById(id);
    }

    /** Solo el docente que creó la pregunta puede modificarla o eliminarla. */
    private void verificarDuenio(Pregunta p, Long docenteId) {
        if (docenteId == null || !docenteId.equals(p.getDocenteId())) {
            throw new AccessDeniedException("La pregunta pertenece a otro docente");
        }
    }

    private void validarReglasPregunta(String tipo, List<OpcionRequest> opciones) {
        if ("ABIERTA".equals(tipo)) {
            return;
        }

        if (opciones == null || opciones.isEmpty()) {
            throw new BusinessRuleException("Las preguntas de tipo " + tipo + " deben tener opciones.");
        }

        long correctas = opciones.stream().filter(o -> Boolean.TRUE.equals(o.getEsCorrecta())).count();

        switch (tipo) {
            case "OPCION_UNICA" -> {
                if (correctas != 1) {
                    throw new BusinessRuleException("Las preguntas de OPCION_UNICA deben tener exactamente una opción marcada como correcta.");
                }
            }
            case "OPCION_MULTIPLE" -> {
                if (correctas < 1) {
                    throw new BusinessRuleException("Las preguntas de OPCION_MULTIPLE deben tener al menos una opción correcta.");
                }
            }
            case "VERDADERO_FALSO" -> {
                if (opciones.size() != 2) {
                    throw new BusinessRuleException("Las preguntas de VERDADERO_FALSO deben tener exactamente dos opciones.");
                }
                if (correctas != 1) {
                    throw new BusinessRuleException("Las preguntas de VERDADERO_FALSO deben tener exactamente una opción verdadera/correcta.");
                }
            }
        }
    }

    public PreguntaResponse toResponse(Pregunta p) {
        List<OpcionResponse> ops = p.getOpciones().stream()
            .map(o -> OpcionResponse.builder()
                .id(o.getId())
                .texto(o.getTexto())
                .esCorrecta(o.getEsCorrecta())
                .orden(o.getOrden())
                .build())
            .toList();

        return PreguntaResponse.builder()
            .id(p.getId())
            .cursoId(p.getCursoId())
            .docenteId(p.getDocenteId())
            .tema(p.getTema())
            .tipo(p.getTipo())
            .enunciado(p.getEnunciado())
            .puntaje(p.getPuntaje())
            .activa(p.getActiva())
            .creadaEn(p.getCreadaEn())
            .opciones(ops)
            .build();
    }
}

