package pe.edu.upeu.eduspace.tareas.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upeu.eduspace.tareas.config.KafkaConfig;
import pe.edu.upeu.eduspace.tareas.dto.Dtos.*;
import pe.edu.upeu.eduspace.tareas.entity.Entrega;
import pe.edu.upeu.eduspace.tareas.entity.Tarea;
import pe.edu.upeu.eduspace.tareas.event.TareaCalificadaEvent;
import pe.edu.upeu.eduspace.tareas.exception.BusinessRuleException;
import pe.edu.upeu.eduspace.tareas.messaging.TareaEventProducer;
import pe.edu.upeu.eduspace.tareas.repository.EntregaRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CalificacionService {

    private static final BigDecimal ESCALA = new BigDecimal("20");

    private final EntregaService entregaService;
    private final TareaService tareaService;
    private final EntregaRepository entregaRepo;
    private final TareaEventProducer producer;

    /** Califica (o recalifica). Publica tarea.calificada con la nota convertida a escala 0–20. */
    @Transactional
    public EntregaResponse calificar(Long entregaId, CalificarRequest req, Long usuarioId, boolean esAdmin) {
        Entrega e = entregaService.obtener(entregaId);
        Tarea t = tareaService.obtener(e.getTareaId());
        tareaService.exigirDueno(t, usuarioId, esAdmin);
        validarNota(req.nota(), t);
        aplicar(e, t, req.nota(), req.retroalimentacion(), usuarioId);
        return entregaService.obtenerEntrega(entregaId);
    }

    @Transactional
    public CalificacionMasivaResultado calificarMasivo(Long tareaId, CalificacionMasivaRequest req, Long usuarioId, boolean esAdmin) {
        Tarea t = tareaService.obtener(tareaId);
        tareaService.exigirDueno(t, usuarioId, esAdmin);
        int ok = 0;
        List<String> errores = new ArrayList<>();
        for (NotaItem it : req.notas()) {
            try {
                Entrega e = entregaService.obtener(it.entregaId());
                if (!e.getTareaId().equals(tareaId)) throw new BusinessRuleException("la entrega no pertenece a esta tarea");
                validarNota(it.nota(), t);
                aplicar(e, t, it.nota(), it.retroalimentacion(), usuarioId);
                ok++;
            } catch (Exception ex) {
                errores.add("Entrega " + it.entregaId() + ": " + ex.getMessage());
            }
        }
        return new CalificacionMasivaResultado(ok, errores);
    }

    /** Devuelve la entrega al alumno para que la corrija y la reenvíe. */
    @Transactional
    public EntregaResponse devolver(Long entregaId, DevolverRequest req, Long usuarioId, boolean esAdmin) {
        Entrega e = entregaService.obtener(entregaId);
        Tarea t = tareaService.obtener(e.getTareaId());
        tareaService.exigirDueno(t, usuarioId, esAdmin);
        if ("CALIFICADA".equals(e.getEstado())) {
            throw new BusinessRuleException("Una entrega CALIFICADA no se puede devolver.");
        }
        if ("CERRADA".equals(t.getEstado())) throw new BusinessRuleException("La tarea está CERRADA.");
        e.setEstado("DEVUELTA");
        e.setRetroalimentacion(req.retroalimentacion());
        entregaRepo.save(e);
        return entregaService.obtenerEntrega(entregaId);
    }

    private void aplicar(Entrega e, Tarea t, BigDecimal nota, String retro, Long usuarioId) {
        if (!"ENTREGADA".equals(e.getEstado()) && !"CALIFICADA".equals(e.getEstado())) {
            throw new BusinessRuleException("Solo se califica una entrega ENTREGADA o ya CALIFICADA (estado " + e.getEstado() + ").");
        }
        e.setNota(nota.setScale(2, RoundingMode.HALF_UP));
        e.setRetroalimentacion(retro);
        e.setEstado("CALIFICADA");
        e.setCalificadaEn(LocalDateTime.now());
        e.setCalificadaPor(usuarioId);
        entregaRepo.save(e);
        producer.publicar(KafkaConfig.TOPIC_TAREA_CALIFICADA, String.valueOf(e.getId()), TareaCalificadaEvent.builder()
            .tareaId(t.getId()).entregaId(e.getId()).alumnoId(e.getAlumnoId()).matriculaId(e.getMatriculaId())
            .seccionId(t.getSeccionId()).cursoId(t.getCursoId()).periodoId(t.getPeriodoId()).titulo(t.getTitulo())
            .nota(aEscala20(e.getNota(), t.getPuntajeMaximo())).puntajeMaximo(t.getPuntajeMaximo()).puntajeObtenido(e.getNota())
            .build());
    }

    private static void validarNota(BigDecimal nota, Tarea t) {
        if (nota.compareTo(t.getPuntajeMaximo()) > 0) {
            throw new BusinessRuleException("La nota (" + nota + ") no puede superar el puntaje máximo (" + t.getPuntajeMaximo() + ").");
        }
    }

    /** nota20 = nota / puntajeMaximo × 20, a 2 decimales. */
    static BigDecimal aEscala20(BigDecimal nota, BigDecimal puntajeMaximo) {
        return nota.multiply(ESCALA).divide(puntajeMaximo, 2, RoundingMode.HALF_UP);
    }
}
