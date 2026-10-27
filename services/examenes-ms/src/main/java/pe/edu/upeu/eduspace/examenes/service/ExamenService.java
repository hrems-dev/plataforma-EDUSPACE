package pe.edu.upeu.eduspace.examenes.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upeu.eduspace.examenes.client.PlanificacionClient;
import pe.edu.upeu.eduspace.examenes.dto.*;
import pe.edu.upeu.eduspace.examenes.entity.*;
import pe.edu.upeu.eduspace.examenes.event.ExamenPublicadoEvent;
import pe.edu.upeu.eduspace.examenes.exception.BusinessRuleException;
import pe.edu.upeu.eduspace.examenes.exception.ResourceNotFoundException;
import pe.edu.upeu.eduspace.examenes.messaging.ExamenEventProducer;
import pe.edu.upeu.eduspace.examenes.repository.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExamenService {

    private final ExamenRepository examenRepository;
    private final ExamenPreguntaRepository examenPreguntaRepository;
    private final PreguntaRepository preguntaRepository;
    private final MatriculaLocalRepository matriculaLocalRepository;
    private final IntentoRepository intentoRepository;
    private final PlanificacionClient planificacionClient;
    private final ExamenEventProducer examenEventProducer;
    private final CalificacionAutomaticaService calificacionAutomaticaService;

    @Transactional(readOnly = true)
    public PageResponse<ExamenResponse> listarExamenes(Long seccionId, Long cursoId, Long docenteId, String estado, Pageable pageable) {
        Page<Examen> page = examenRepository.findExamenesFiltrados(seccionId, cursoId, docenteId, estado, pageable);
        return PageResponse.from(page.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public ExamenDetalleResponse obtenerPorId(Long id) {
        Examen e = examenRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Examen no encontrado con ID: " + id));

        List<ExamenDetalleResponse.PreguntaItemDetalle> preguntasDetalle = new ArrayList<>();
        for (ExamenPregunta ep : e.getPreguntas()) {
            Pregunta p = ep.getPregunta();
            List<OpcionResponse> opciones = p.getOpciones().stream()
                .map(o -> OpcionResponse.builder()
                    .id(o.getId())
                    .texto(o.getTexto())
                    .esCorrecta(o.getEsCorrecta())
                    .orden(o.getOrden())
                    .build())
                .toList();

            preguntasDetalle.add(ExamenDetalleResponse.PreguntaItemDetalle.builder()
                .preguntaId(p.getId())
                .orden(ep.getOrden())
                .puntaje(ep.getPuntaje())
                .tema(p.getTema())
                .tipo(p.getTipo())
                .enunciado(p.getEnunciado())
                .opciones(opciones)
                .build());
        }

        return ExamenDetalleResponse.builder()
            .id(e.getId())
            .anioId(e.getAnioId())
            .periodoId(e.getPeriodoId())
            .seccionId(e.getSeccionId())
            .cursoId(e.getCursoId())
            .docenteId(e.getDocenteId())
            .titulo(e.getTitulo())
            .instrucciones(e.getInstrucciones())
            .fechaInicio(e.getFechaInicio())
            .fechaFin(e.getFechaFin())
            .duracionMin(e.getDuracionMin())
            .intentosMax(e.getIntentosMax())
            .barajarPreguntas(e.getBarajarPreguntas())
            .puntajeTotal(e.getPuntajeTotal())
            .estado(e.getEstado())
            .preguntas(preguntasDetalle)
            .build();
    }

    @Transactional
    public ExamenResponse crear(ExamenCreateRequest req, Long docenteId) {
        validarFechas(req);
        // docenteId == null => ADMIN: se crea a nombre de la asignación vigente, sin validar dueño
        if (docenteId == null) {
            throw new BusinessRuleException("El ADMIN no puede crear exámenes sin un docente asignado; use una cuenta de docente.");
        }
        validarAsignacion(docenteId, req.getSeccionId(), req.getCursoId());

        Examen examen = Examen.builder()
            .anioId(req.getAnioId())
            .periodoId(req.getPeriodoId())
            .seccionId(req.getSeccionId())
            .cursoId(req.getCursoId())
            .docenteId(docenteId)
            .titulo(req.getTitulo())
            .instrucciones(req.getInstrucciones())
            .fechaInicio(req.getFechaInicio())
            .fechaFin(req.getFechaFin())
            .duracionMin(req.getDuracionMin())
            .intentosMax(req.getIntentosMax() != null ? req.getIntentosMax() : 1)
            .barajarPreguntas(Boolean.TRUE.equals(req.getBarajarPreguntas()))
            .estado("BORRADOR")
            .creadoEn(LocalDateTime.now())
            .build();

        Examen guardado = examenRepository.save(examen);
        asignarPreguntas(guardado, req.getPreguntas());
        return toResponse(examenRepository.save(guardado));
    }

    @Transactional
    public ExamenResponse actualizar(Long id, ExamenCreateRequest req, Long docenteId) {
        Examen e = examenRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Examen no encontrado: " + id));
        verificarDueno(e, docenteId);
        if (!"BORRADOR".equals(e.getEstado())) {
            throw new BusinessRuleException("Solo se pueden editar exámenes en estado BORRADOR.");
        }
        validarFechas(req);
        validarAsignacion(e.getDocenteId(), req.getSeccionId(), req.getCursoId());

        e.setAnioId(req.getAnioId());
        e.setPeriodoId(req.getPeriodoId());
        e.setSeccionId(req.getSeccionId());
        e.setCursoId(req.getCursoId());
        e.setTitulo(req.getTitulo());
        e.setInstrucciones(req.getInstrucciones());
        e.setFechaInicio(req.getFechaInicio());
        e.setFechaFin(req.getFechaFin());
        e.setDuracionMin(req.getDuracionMin());
        e.setIntentosMax(req.getIntentosMax() != null ? req.getIntentosMax() : 1);
        e.setBarajarPreguntas(Boolean.TRUE.equals(req.getBarajarPreguntas()));
        e.getPreguntas().clear();
        examenRepository.saveAndFlush(e);
        asignarPreguntas(e, req.getPreguntas());
        return toResponse(examenRepository.save(e));
    }

    private void validarFechas(ExamenCreateRequest req) {
        if (!req.getFechaFin().isAfter(req.getFechaInicio())) {
            throw new BusinessRuleException("La fecha fin debe ser posterior a la fecha inicio.");
        }
    }

    private void validarAsignacion(Long docenteId, Long seccionId, Long cursoId) {
        Boolean asignado = planificacionClient.validarAsignacionDocente(docenteId, seccionId, cursoId);
        if (!Boolean.TRUE.equals(asignado)) {
            throw new BusinessRuleException(String.format(
                "El docente %d no tiene asignación vigente en la sección %d para el curso %d.", docenteId, seccionId, cursoId));
        }
    }

    private void verificarDueno(Examen e, Long docenteId) {
        if (docenteId != null && !docenteId.equals(e.getDocenteId())) {
            throw new org.springframework.security.access.AccessDeniedException("El examen pertenece a otro docente.");
        }
    }

    private void asignarPreguntas(Examen examen, List<ExamenPreguntaItem> items) {
        BigDecimal puntajeTotal = BigDecimal.ZERO;
        List<ExamenPregunta> lista = new ArrayList<>();
        for (ExamenPreguntaItem item : items) {
            Pregunta preg = preguntaRepository.findById(item.getPreguntaId())
                .orElseThrow(() -> new ResourceNotFoundException("Pregunta no encontrada: " + item.getPreguntaId()));
            lista.add(examenPreguntaRepository.save(ExamenPregunta.builder()
                .examen(examen).pregunta(preg).orden(item.getOrden()).puntaje(item.getPuntaje()).build()));
            puntajeTotal = puntajeTotal.add(item.getPuntaje());
        }
        examen.setPuntajeTotal(puntajeTotal);
        examen.getPreguntas().clear();
        examen.getPreguntas().addAll(lista);
    }

    @Transactional
    public ExamenResponse publicar(Long id, Long docenteId) {
        Examen e = examenRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Examen no encontrado: " + id));
        verificarDueno(e, docenteId);

        if (!"BORRADOR".equals(e.getEstado())) {
            throw new BusinessRuleException("Solo se pueden publicar exámenes en estado BORRADOR.");
        }
        if (e.getPreguntas().isEmpty()) {
            throw new BusinessRuleException("El examen no contiene preguntas.");
        }
        if (!e.getFechaFin().isAfter(LocalDateTime.now())) {
            throw new BusinessRuleException("La ventana del examen ya terminó; ajuste las fechas antes de publicar.");
        }
        validarAsignacion(e.getDocenteId(), e.getSeccionId(), e.getCursoId());

        e.setEstado("PUBLICADO");
        e.setPublicadoEn(LocalDateTime.now());
        Examen guardado = examenRepository.save(e);

        List<MatriculaLocal> matriculas = matriculaLocalRepository.findBySeccionIdAndEstado(e.getSeccionId(), "MATRICULADO");
        List<Long> alumnoIds = matriculas.stream().map(MatriculaLocal::getAlumnoId).toList();

        Map<String, Object> cursoInfo = planificacionClient.getCursoPorId(e.getCursoId());
        String cursoNombre = cursoInfo != null && cursoInfo.containsKey("nombre") ? cursoInfo.get("nombre").toString() : "Curso " + e.getCursoId();

        examenEventProducer.publicarExamenPublicado(ExamenPublicadoEvent.builder()
            .examenId(e.getId())
            .seccionId(e.getSeccionId())
            .cursoId(e.getCursoId())
            .cursoNombre(cursoNombre)
            .titulo(e.getTitulo())
            .fechaInicio(e.getFechaInicio())
            .fechaFin(e.getFechaFin())
            .alumnoIds(alumnoIds)
            .build());

        log.info("Examen ID {} publicado exitosamente para {} alumnos.", e.getId(), alumnoIds.size());
        return toResponse(guardado);
    }

    @Transactional
    public ExamenResponse cerrar(Long id, Long docenteId) {
        Examen e = examenRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Examen no encontrado: " + id));
        verificarDueno(e, docenteId);
        if ("CERRADO".equals(e.getEstado())) {
            throw new BusinessRuleException("El examen ya está cerrado.");
        }
        if ("BORRADOR".equals(e.getEstado())) {
            throw new BusinessRuleException("Un examen en BORRADOR no se cierra; elimínelo o publíquelo.");
        }
        // Los intentos abiertos se cierran y califican con lo respondido hasta ahora
        for (Intento i : intentoRepository.findByExamenId(e.getId())) {
            if ("EN_CURSO".equals(i.getEstado())) {
                i.setEstado("VENCIDO");
                i.setEnviadoEn(LocalDateTime.now());
                intentoRepository.save(i);
                calificacionAutomaticaService.calificarIntento(i.getId());
            }
        }
        e.setEstado("CERRADO");
        return toResponse(examenRepository.save(e));
    }

    @Transactional(readOnly = true)
    public List<ExamenDisponibleResponse> listarDisponiblesAlumno(Long alumnoId) {
        List<MatriculaLocal> matriculas = matriculaLocalRepository.findByAlumnoId(alumnoId);
        if (matriculas.isEmpty()) {
            return List.of();
        }

        LocalDateTime ahora = LocalDateTime.now();
        List<ExamenDisponibleResponse> disponibles = new ArrayList<>();

        for (MatriculaLocal m : matriculas) {
            if ("MATRICULADO".equals(m.getEstado())) {
                List<Examen> examenes = examenRepository.findExamenesDisponiblesAlumno(m.getSeccionId(), ahora);
                for (Examen e : examenes) {
                    short intentosUsados = intentoRepository.countByExamenIdAndMatriculaId(e.getId(), m.getMatriculaId());
                    boolean puedeRendir = intentosUsados < e.getIntentosMax();

                    disponibles.add(ExamenDisponibleResponse.builder()
                        .examenId(e.getId())
                        .cursoId(e.getCursoId())
                        .titulo(e.getTitulo())
                        .instrucciones(e.getInstrucciones())
                        .fechaInicio(e.getFechaInicio())
                        .fechaFin(e.getFechaFin())
                        .duracionMin(e.getDuracionMin())
                        .intentosMax(e.getIntentosMax())
                        .intentosRealizados(intentosUsados)
                        .puedeRendir(puedeRendir)
                        .puntajeTotal(e.getPuntajeTotal())
                        .build());
                }
            }
        }

        return disponibles;
    }

    public ExamenResponse toResponse(Examen e) {
        return ExamenResponse.builder()
            .id(e.getId())
            .anioId(e.getAnioId())
            .periodoId(e.getPeriodoId())
            .seccionId(e.getSeccionId())
            .cursoId(e.getCursoId())
            .docenteId(e.getDocenteId())
            .titulo(e.getTitulo())
            .instrucciones(e.getInstrucciones())
            .fechaInicio(e.getFechaInicio())
            .fechaFin(e.getFechaFin())
            .duracionMin(e.getDuracionMin())
            .intentosMax(e.getIntentosMax())
            .barajarPreguntas(e.getBarajarPreguntas())
            .puntajeTotal(e.getPuntajeTotal())
            .estado(e.getEstado())
            .creadoEn(e.getCreadoEn())
            .publicadoEn(e.getPublicadoEn())
            .build();
    }
}

