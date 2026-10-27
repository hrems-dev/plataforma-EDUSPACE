package pe.edu.upeu.eduspace.tareas.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upeu.eduspace.tareas.client.PlanificacionGateway;
import pe.edu.upeu.eduspace.tareas.config.KafkaConfig;
import pe.edu.upeu.eduspace.tareas.entity.Entrega;
import pe.edu.upeu.eduspace.tareas.entity.MatriculaLocal;
import pe.edu.upeu.eduspace.tareas.entity.Tarea;
import pe.edu.upeu.eduspace.tareas.event.TareaPublicadaEvent;
import pe.edu.upeu.eduspace.tareas.messaging.TareaEventProducer;
import pe.edu.upeu.eduspace.tareas.repository.EntregaRepository;
import pe.edu.upeu.eduspace.tareas.repository.MatriculaLocalRepository;
import pe.edu.upeu.eduspace.tareas.repository.TareaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Cada hora avisa (una sola vez por tarea) a quienes no han entregado una tarea que vence en menos de 24 h. */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecordatorioJob {

    private final TareaRepository tareaRepo;
    private final EntregaRepository entregaRepo;
    private final MatriculaLocalRepository matriculaRepo;
    private final PlanificacionGateway planificacion;
    private final TareaEventProducer producer;

    @Scheduled(cron = "0 5 * * * *", zone = "America/Lima")
    @SchedulerLock(name = "tareas_recordatorio_job", lockAtLeastFor = "30s", lockAtMostFor = "10m")
    @Transactional
    public void enviarRecordatorios() {
        ejecutar();
    }

    public int ejecutar() {
        LocalDateTime ahora = LocalDateTime.now();
        int n = 0;
        for (Tarea t : tareaRepo.porVencer(ahora, ahora.plusHours(24))) {
            Set<Long> entregaron = entregaRepo.findByTareaIdOrderByEntregadaEnAsc(t.getId()).stream()
                .filter(e -> !"DEVUELTA".equals(e.getEstado())).map(Entrega::getAlumnoId).collect(Collectors.toSet());
            List<Long> pendientes = matriculaRepo.findBySeccionIdAndEstadoOrderByAlumnoNombre(t.getSeccionId(), "MATRICULADO").stream()
                .map(MatriculaLocal::getAlumnoId).filter(a -> !entregaron.contains(a)).toList();
            t.setRecordatorioEnviado(true);
            tareaRepo.save(t);
            if (!pendientes.isEmpty()) {
                producer.publicar(KafkaConfig.TOPIC_TAREA_RECORDATORIO, String.valueOf(t.getId()), TareaPublicadaEvent.builder()
                    .tareaId(t.getId()).seccionId(t.getSeccionId()).cursoId(t.getCursoId())
                    .cursoNombre(planificacion.nombreCursoOpcional(t.getCursoId())).titulo(t.getTitulo())
                    .fechaEntrega(t.getFechaEntrega()).alumnoIds(pendientes).build());
                n++;
            }
        }
        if (n > 0) log.info("Recordatorio enviado para {} tarea(s)", n);
        return n;
    }
}
