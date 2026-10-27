package pe.edu.upeu.eduspace.tareas.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upeu.eduspace.tareas.client.MatriculaClient;
import pe.edu.upeu.eduspace.tareas.config.KafkaConfig;
import pe.edu.upeu.eduspace.tareas.entity.EventoProcesado;
import pe.edu.upeu.eduspace.tareas.entity.MatriculaLocal;
import pe.edu.upeu.eduspace.tareas.event.EventoSobre;
import pe.edu.upeu.eduspace.tareas.event.MatriculaEvent;
import pe.edu.upeu.eduspace.tareas.repository.EventoProcesadoRepository;
import pe.edu.upeu.eduspace.tareas.repository.MatriculaLocalRepository;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatriculaLocalService {

    private final MatriculaLocalRepository repo;
    private final EventoProcesadoRepository eventoRepo;
    private final MatriculaClient matriculaClient;

    /** UPSERT idempotente de la réplica. Un evento más viejo que lo ya aplicado se registra pero no se aplica. */
    @Transactional
    public void procesar(EventoSobre<MatriculaEvent> sobre) {
        if (sobre.getEventId() != null && eventoRepo.existsByEventId(sobre.getEventId())) {
            log.info("Evento {} ya procesado; se ignora.", sobre.getEventId());
            return;
        }
        MatriculaEvent ev = sobre.getPayload();
        if (ev.getMatriculaId() == null || ev.getAlumnoId() == null || ev.getSeccionId() == null) {
            log.warn("Evento {} incompleto descartado: {}", sobre.getEventType(), ev);
            return;
        }
        LocalDateTime cuando = sobre.getOccurredAt() != null ? sobre.getOccurredAt() : LocalDateTime.now();
        MatriculaLocal actual = repo.findById(ev.getMatriculaId()).orElse(null);
        if (actual != null && cuando.isBefore(actual.getActualizadoEn())) {
            log.info("Evento {} de la matrícula {} es más viejo que lo aplicado; se ignora.", sobre.getEventType(), ev.getMatriculaId());
        } else {
            repo.save(MatriculaLocal.builder()
                .matriculaId(ev.getMatriculaId())
                .alumnoId(ev.getAlumnoId())
                .alumnoNombre(ev.getAlumnoNombre() != null ? ev.getAlumnoNombre() : (actual != null ? actual.getAlumnoNombre() : "Alumno " + ev.getAlumnoId()))
                .anioId(ev.getAnioId() != null ? ev.getAnioId() : actual != null ? actual.getAnioId() : 0L)
                .nivelId(ev.getNivelId() != null ? ev.getNivelId() : actual != null ? actual.getNivelId() : null)
                .gradoId(ev.getGradoId() != null ? ev.getGradoId() : actual != null ? actual.getGradoId() : 0L)
                .seccionId(ev.getSeccionId())
                .estado(estadoDe(sobre.getEventType(), ev.getEstado()))
                .actualizadoEn(cuando)
                .build());
        }
        if (sobre.getEventId() != null) {
            eventoRepo.save(EventoProcesado.builder().eventId(sobre.getEventId())
                .eventType(sobre.getEventType() != null ? sobre.getEventType() : "matricula.*").build());
        }
    }

    static String estadoDe(String topic, String estadoEvento) {
        if (topic != null) {
            switch (topic) {
                case KafkaConfig.TOPIC_MATRICULA_CREADA: return "PRE_MATRICULA";
                case KafkaConfig.TOPIC_MATRICULA_CONFIRMADA: return "MATRICULADO";
                case KafkaConfig.TOPIC_MATRICULA_RETIRADA: return "RETIRADO";
                case KafkaConfig.TOPIC_MATRICULA_ANULADA: return "ANULADO";
                default: break;
            }
        }
        return estadoEvento != null ? estadoEvento : "MATRICULADO";
    }

    /** Carga inicial desde matricula-ms cuando la sección no tiene alumnos en la réplica. */
    @Transactional
    public int cargarInicialSiVacia(Long seccionId) {
        if (!repo.findBySeccionId(seccionId).isEmpty()) return 0;
        int n = 0;
        try {
            for (MatriculaClient.MatriculaInterna m : matriculaClient.deSeccion(seccionId)) {
                repo.save(MatriculaLocal.builder().matriculaId(m.matriculaId()).alumnoId(m.alumnoId())
                    .alumnoNombre(m.alumnoNombre()).anioId(m.anioId()).nivelId(m.nivelId()).gradoId(m.gradoId())
                    .seccionId(m.seccionId()).estado(m.estado()).build());
                n++;
            }
            log.info("Carga inicial de la sección {}: {} matrícula(s)", seccionId, n);
        } catch (Exception e) {
            log.warn("No se pudo hacer la carga inicial de la sección {}: {}", seccionId, e.getMessage());
        }
        return n;
    }
}
