package pe.edu.upeu.eduspace.examenes.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upeu.eduspace.examenes.config.ExamenProperties;
import pe.edu.upeu.eduspace.examenes.dto.IntentoIniciadoResponse;
import pe.edu.upeu.eduspace.examenes.dto.IntentoResultadoResponse;
import pe.edu.upeu.eduspace.examenes.entity.*;
import pe.edu.upeu.eduspace.examenes.exception.BusinessRuleException;
import pe.edu.upeu.eduspace.examenes.exception.ResourceNotFoundException;
import pe.edu.upeu.eduspace.examenes.repository.*;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class IntentoService {

    private final ExamenRepository examenRepository;
    private final IntentoRepository intentoRepository;
    private final MatriculaLocalRepository matriculaLocalRepository;
    private final RespuestaRepository respuestaRepository;
    private final CalificacionAutomaticaService calificacionAutomaticaService;
    private final ExamenProperties examenProperties;

    @Transactional
    public IntentoIniciadoResponse iniciarIntento(Long examenId, Long alumnoId) {
        Examen examen = examenRepository.findById(examenId)
            .orElseThrow(() -> new ResourceNotFoundException("Examen no encontrado: " + examenId));

        if (!"PUBLICADO".equals(examen.getEstado())) {
            throw new BusinessRuleException("El examen no se encuentra en estado PUBLICADO.");
        }

        LocalDateTime ahora = LocalDateTime.now();
        if (ahora.isBefore(examen.getFechaInicio()) || ahora.isAfter(examen.getFechaFin())) {
            throw new BusinessRuleException("El examen no está en su ventana de fechas habilitada.");
        }

        MatriculaLocal matricula = matriculaLocalRepository.findByAlumnoIdAndSeccionIdAndEstado(alumnoId, examen.getSeccionId(), "MATRICULADO")
            .orElseThrow(() -> new BusinessRuleException("El alumno no está matriculado en la sección correspondiente a este examen."));

        // 1. Verificar si ya tiene un intento EN_CURSO vigente (Reanudación por corte de red)
        Optional<Intento> intentoActivo = intentoRepository.findFirstByExamenIdAndMatriculaIdAndEstado(examenId, matricula.getMatriculaId(), "EN_CURSO");
        if (intentoActivo.isPresent()) {
            Intento activo = intentoActivo.get();
            if (ahora.isBefore(activo.getVenceEn())) {
                log.info("Reanudando intento ID {} para alumno {}", activo.getId(), alumnoId);
                return construirIntentoResponse(activo, examen);
            } else {
                // Venció mientras estaba desconectado: cerrarlo y calificar
                activo.setEstado("VENCIDO");
                intentoRepository.save(activo);
                calificacionAutomaticaService.calificarIntento(activo.getId());
            }
        }

        // 2. Verificar número de intentos disponibles
        short usados = intentoRepository.countByExamenIdAndMatriculaId(examenId, matricula.getMatriculaId());
        if (usados >= examen.getIntentosMax()) {
            throw new BusinessRuleException(String.format("Ha alcanzado el límite máximo de intentos permitidos (%d).", examen.getIntentosMax()));
        }

        // 3. Calcular vencimiento en SERVIDOR: min(iniciado + duracion, fecha_fin)
        LocalDateTime duracionFin = ahora.plusMinutes(examen.getDuracionMin());
        LocalDateTime venceEn = duracionFin.isBefore(examen.getFechaFin()) ? duracionFin : examen.getFechaFin();

        Intento nuevoIntento = Intento.builder()
            .examen(examen)
            .matriculaId(matricula.getMatriculaId())
            .alumnoId(alumnoId)
            .numero((short) (usados + 1))
            .iniciadoEn(ahora)
            .venceEn(venceEn)
            .estado("EN_CURSO")
            .build();

        Intento guardado = intentoRepository.save(nuevoIntento);
        log.info("Iniciado intento ID {} (número {}) para alumno {}", guardado.getId(), guardado.getNumero(), alumnoId);

        return construirIntentoResponse(guardado, examen);
    }

    @Transactional
    public IntentoResultadoResponse enviarIntento(Long intentoId, Long alumnoId) {
        Intento intento = intentoRepository.findById(intentoId)
            .orElseThrow(() -> new ResourceNotFoundException("Intento no encontrado: " + intentoId));

        if (!intento.getAlumnoId().equals(alumnoId)) {
            throw new BusinessRuleException("No está autorizado para enviar este intento.");
        }

        if (!"EN_CURSO".equals(intento.getEstado())) {
            throw new BusinessRuleException("El intento ya fue enviado o cerrado previamente.");
        }

        LocalDateTime ahora = LocalDateTime.now();
        LocalDateTime limiteConGracia = intento.getVenceEn().plusSeconds(examenProperties.getMargenGraciaSegundos());

        intento.setEnviadoEn(ahora);
        if (ahora.isAfter(limiteConGracia)) {
            log.warn("Intento {} enviado después del límite con gracia. Marcado como VENCIDO.", intentoId);
            intento.setEstado("VENCIDO");
        } else {
            intento.setEstado("ENVIADO");
        }
        intentoRepository.save(intento);

        // Disparar calificación
        return calificacionAutomaticaService.calificarIntento(intentoId);
    }

    @Transactional(readOnly = true)
    public IntentoResultadoResponse obtenerResultado(Long intentoId) {
        Intento intento = intentoRepository.findById(intentoId)
            .orElseThrow(() -> new ResourceNotFoundException("Intento no encontrado: " + intentoId));

        boolean requiereRev = intento.getRespuestas().stream().anyMatch(Respuesta::getRequiereRevision);

        return IntentoResultadoResponse.builder()
            .intentoId(intento.getId())
            .examenId(intento.getExamen().getId())
            .tituloExamen(intento.getExamen().getTitulo())
            .numeroIntento(intento.getNumero())
            .estado(intento.getEstado())
            .puntajeObtenido(intento.getPuntajeObtenido())
            .puntajeTotal(intento.getExamen().getPuntajeTotal())
            .nota(intento.getNota())
            .iniciadoEn(intento.getIniciadoEn())
            .enviadoEn(intento.getEnviadoEn())
            .calificadoEn(intento.getCalificadoEn())
            .requiereRevisionAbiertas(requiereRev)
            .build();
    }

    private IntentoIniciadoResponse construirIntentoResponse(Intento intento, Examen examen) {
        List<ExamenPregunta> examenPreguntas = new ArrayList<>(examen.getPreguntas());
        if (Boolean.TRUE.equals(examen.getBarajarPreguntas())) {
            // Barajado determinista usando hash del intento para consistencia en reconexiones
            Collections.shuffle(examenPreguntas, new Random(intento.getId()));
        }

        Map<Long, Respuesta> respuestasPrevias = new HashMap<>();
        for (Respuesta r : intento.getRespuestas()) {
            respuestasPrevias.put(r.getPregunta().getId(), r);
        }

        List<IntentoIniciadoResponse.PreguntaEstudianteDto> listaPreguntas = new ArrayList<>();
        short orden = 1;
        for (ExamenPregunta ep : examenPreguntas) {
            Pregunta p = ep.getPregunta();
            Respuesta previa = respuestasPrevias.get(p.getId());

            List<IntentoIniciadoResponse.OpcionEstudianteDto> opcionesEst = p.getOpciones().stream()
                .map(o -> IntentoIniciadoResponse.OpcionEstudianteDto.builder()
                    .id(o.getId())
                    .texto(o.getTexto())
                    .orden(o.getOrden())
                    .build())
                .toList();

            listaPreguntas.add(IntentoIniciadoResponse.PreguntaEstudianteDto.builder()
                .preguntaId(p.getId())
                .orden(orden++)
                .puntaje(ep.getPuntaje())
                .tipo(p.getTipo())
                .enunciado(p.getEnunciado())
                .opciones(opcionesEst)
                .opcionesRespondidas(previa != null ? previa.getOpcionesIds() : null)
                .textoRespondido(previa != null ? previa.getTextoRespuesta() : null)
                .build());
        }

        return IntentoIniciadoResponse.builder()
            .intentoId(intento.getId())
            .examenId(examen.getId())
            .tituloExamen(examen.getTitulo())
            .numeroIntento(intento.getNumero())
            .iniciadoEn(intento.getIniciadoEn())
            .venceEn(intento.getVenceEn())
            .puntajeTotal(examen.getPuntajeTotal())
            .preguntas(listaPreguntas)
            .build();
    }
}

