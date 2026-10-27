package pe.edu.upeu.eduspace.calificaciones.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upeu.eduspace.calificaciones.dto.NotaPendienteResponse;
import pe.edu.upeu.eduspace.calificaciones.entity.CierrePeriodo;
import pe.edu.upeu.eduspace.calificaciones.entity.CriterioEvaluacion;
import pe.edu.upeu.eduspace.calificaciones.entity.EventoProcesado;
import pe.edu.upeu.eduspace.calificaciones.entity.Nota;
import pe.edu.upeu.eduspace.calificaciones.event.ExamenCalificadoEvent;
import pe.edu.upeu.eduspace.calificaciones.event.TareaCalificadaEvent;
import pe.edu.upeu.eduspace.calificaciones.repository.CierrePeriodoRepository;
import pe.edu.upeu.eduspace.calificaciones.repository.CriterioEvaluacionRepository;
import pe.edu.upeu.eduspace.calificaciones.repository.EventoProcesadoRepository;
import pe.edu.upeu.eduspace.calificaciones.entity.NotaPendiente;
import pe.edu.upeu.eduspace.calificaciones.repository.NotaPendienteRepository;
import pe.edu.upeu.eduspace.calificaciones.repository.NotaRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotaEventoService {

    private final NotaRepository notaRepository;
    private final CriterioEvaluacionRepository criterioRepository;
    private final CierrePeriodoRepository cierrePeriodoRepository;
    private final EventoProcesadoRepository eventoProcesadoRepository;
    private final PromedioService promedioService;
    private final NotaPendienteRepository pendienteRepository;

    @Transactional
    public void procesarTareaCalificada(UUID eventId, TareaCalificadaEvent event) {
        if (eventId != null && eventoProcesadoRepository.existsByEventId(eventId)) {
            log.info("Evento tarea.calificada ya procesado previamente: {}", eventId);
            return;
        }

        procesarNotaExterna(
            "TAREA",
            event.getTareaId(),
            event.getTitulo(),
            event.getAlumnoId(),
            event.getMatriculaId(),
            event.getSeccionId(),
            event.getCursoId(),
            event.getPeriodoId(),
            event.getNota()
        );

        if (eventId != null) {
            eventoProcesadoRepository.save(EventoProcesado.builder()
                .eventId(eventId)
                .eventType("tarea.calificada")
                .procesadoEn(LocalDateTime.now())
                .build());
        }
    }

    @Transactional
    public void procesarExamenCalificado(UUID eventId, ExamenCalificadoEvent event) {
        if (eventId != null && eventoProcesadoRepository.existsByEventId(eventId)) {
            log.info("Evento examen.calificado ya procesado previamente: {}", eventId);
            return;
        }

        procesarNotaExterna(
            "EXAMEN",
            event.getExamenId(),
            event.getTitulo(),
            event.getAlumnoId(),
            event.getMatriculaId(),
            event.getSeccionId(),
            event.getCursoId(),
            event.getPeriodoId(),
            event.getNota()
        );

        if (eventId != null) {
            eventoProcesadoRepository.save(EventoProcesado.builder()
                .eventId(eventId)
                .eventType("examen.calificado")
                .procesadoEn(LocalDateTime.now())
                .build());
        }
    }

    private void procesarNotaExterna(String origen,
                                     Long origenId,
                                     String titulo,
                                     Long alumnoId,
                                     Long matriculaId,
                                     Long seccionId,
                                     Long cursoId,
                                     Long periodoId,
                                     BigDecimal valor) {
        if (matriculaId == null || cursoId == null || periodoId == null || valor == null) {
            log.warn("Evento de nota incompleto recibido de origen {}: origenId={}", origen, origenId);
            return;
        }

        // 1. Verificar si el periodo está CERRADO
        Optional<CierrePeriodo> cierreOpt = cierrePeriodoRepository.findBySeccionIdAndPeriodoId(seccionId, periodoId);
        if (cierreOpt.isPresent() && "CERRADO".equalsIgnoreCase(cierreOpt.get().getEstado())) {
            log.warn("ALERTA DOCENTE: periodo {} de la sección {} CERRADO; la nota de {} {} del alumno {} no se aplica (reabra el periodo para incorporarla).",
                periodoId, seccionId, origen, origenId, alumnoId);
            guardarPendiente("PERIODO_CERRADO", origen, origenId, titulo, alumnoId, matriculaId, seccionId, cursoId, periodoId, valor);
            return;
        }

        // 2. Buscar criterio correspondiente (origenTipo: 'TAREAS' o 'EXAMENES')
        String tipoBuscado = "TAREA".equalsIgnoreCase(origen) ? "TAREAS" : "EXAMENES";
        Optional<CriterioEvaluacion> criterioOpt = criterioRepository.findFirstByPeriodoIdAndSeccionIdAndCursoIdAndOrigenTipo(
            periodoId, seccionId, cursoId, tipoBuscado
        );

        if (criterioOpt.isEmpty()) {
            // El docente no ha creado el criterio aún -> guardar en cola de pendientes visible al docente
            log.info("No existe criterio de tipo {} para curso={}, periodo={}, seccion={}. Guardando en pendientes.",
                tipoBuscado, cursoId, periodoId, seccionId);

            guardarPendiente("PENDIENTE", origen, origenId, titulo, alumnoId, matriculaId, seccionId, cursoId, periodoId, valor);
            return;
        }

        aplicarNota(criterioOpt.get(), origen, origenId, alumnoId, matriculaId, seccionId, cursoId, periodoId, valor);
    }

    private void guardarPendiente(String estado, String origen, Long origenId, String titulo, Long alumnoId,
                                  Long matriculaId, Long seccionId, Long cursoId, Long periodoId, BigDecimal valor) {
        NotaPendiente p = pendienteRepository.findByMatriculaIdAndOrigenAndOrigenId(matriculaId, origen, origenId)
            .orElseGet(() -> NotaPendiente.builder()
                .origen(origen).origenId(origenId).matriculaId(matriculaId).build());
        p.setTitulo(titulo != null ? titulo : origen + " " + origenId);
        p.setAlumnoId(alumnoId);
        p.setSeccionId(seccionId);
        p.setCursoId(cursoId);
        p.setPeriodoId(periodoId);
        p.setValor(valor);
        p.setEstado(estado);
        p.setFechaEvento(LocalDateTime.now());
        p.setAplicadaEn(null);
        pendienteRepository.save(p);
    }

    private void aplicarNota(CriterioEvaluacion criterio, String origen, Long origenId, Long alumnoId, Long matriculaId,
                             Long seccionId, Long cursoId, Long periodoId, BigDecimal valor) {
        // 3. UPSERT por uk_nota_origen: (matricula_id, criterio_id, origen, origen_id)
        Nota nota = notaRepository.findByCriterioAndMatriculaAndOrigen(criterio.getId(), matriculaId, origen, origenId)
            .orElseGet(() -> Nota.builder()
                .matriculaId(matriculaId)
                .alumnoId(alumnoId)
                .seccionId(seccionId)
                .cursoId(cursoId)
                .periodoId(periodoId)
                .criterio(criterio)
                .origen(origen)
                .origenId(origenId)
                .registradoEn(LocalDateTime.now())
                .build());

        nota.setValor(valor);
        nota.setActualizadoEn(LocalDateTime.now());
        notaRepository.save(nota);

        // 4. Recalcular promedio ponderado
        promedioService.calcularYGuardarPromedio(matriculaId, cursoId, periodoId);

        log.info("Nota de {} aplicada exitosamente: matricula={}, curso={}, valor={}", origen, matriculaId, cursoId, valor);
        pendienteRepository.findByMatriculaIdAndOrigenAndOrigenId(matriculaId, origen, origenId).ifPresent(p -> {
            p.setEstado("APLICADA");
            p.setAplicadaEn(LocalDateTime.now());
            pendienteRepository.save(p);
        });
    }

    /** Al crear un criterio TAREAS/EXAMENES: incorpora las notas que esperaban por él. */
    @Transactional
    public int aplicarPendientes(CriterioEvaluacion criterio) {
        String origen = "TAREAS".equalsIgnoreCase(criterio.getOrigenTipo()) ? "TAREA"
                      : "EXAMENES".equalsIgnoreCase(criterio.getOrigenTipo()) ? "EXAMEN" : null;
        if (origen == null) return 0;
        List<NotaPendiente> lista = pendienteRepository.findByPeriodoIdAndSeccionIdAndCursoIdAndOrigenAndEstado(
            criterio.getPeriodoId(), criterio.getSeccionId(), criterio.getCursoId(), origen, "PENDIENTE");
        for (NotaPendiente p : lista) {
            aplicarNota(criterio, origen, p.getOrigenId(), p.getAlumnoId(), p.getMatriculaId(),
                p.getSeccionId(), p.getCursoId(), p.getPeriodoId(), p.getValor());
        }
        return lista.size();
    }

    /** Tras reabrir un periodo: las notas que llegaron con el periodo cerrado se intentan aplicar. */
    @Transactional
    public void reprocesarPeriodoReabierto(Long seccionId, Long periodoId) {
        for (NotaPendiente p : pendienteRepository.findAbiertas(seccionId, null)) {
            if (!"PERIODO_CERRADO".equals(p.getEstado()) || !periodoId.equals(p.getPeriodoId())) continue;
            p.setEstado("PENDIENTE");
            pendienteRepository.save(p);
            String tipo = "TAREA".equals(p.getOrigen()) ? "TAREAS" : "EXAMENES";
            criterioRepository.findFirstByPeriodoIdAndSeccionIdAndCursoIdAndOrigenTipo(periodoId, seccionId, p.getCursoId(), tipo)
                .ifPresent(c -> aplicarNota(c, p.getOrigen(), p.getOrigenId(), p.getAlumnoId(), p.getMatriculaId(),
                    p.getSeccionId(), p.getCursoId(), p.getPeriodoId(), p.getValor()));
        }
    }

    @Transactional(readOnly = true)
    public List<NotaPendienteResponse> listarPendientes(Long seccionId, Long cursoId) {
        return pendienteRepository.findAbiertas(seccionId, cursoId).stream()
            .map(p -> NotaPendienteResponse.builder()
                .id(String.valueOf(p.getId()))
                .origen(p.getOrigen())
                .origenId(p.getOrigenId())
                .titulo(p.getTitulo())
                .alumnoId(p.getAlumnoId())
                .matriculaId(p.getMatriculaId())
                .seccionId(p.getSeccionId())
                .cursoId(p.getCursoId())
                .periodoId(p.getPeriodoId())
                .valor(p.getValor())
                .fechaEvento(p.getFechaEvento())
                .motivo(p.getEstado())
                .build())
            .toList();
    }
}
