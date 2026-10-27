package pe.edu.upeu.eduspace.tareas.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upeu.eduspace.tareas.entity.Tarea;
import pe.edu.upeu.eduspace.tareas.repository.TareaRepository;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class CierreAutomaticoJob {

    private final TareaRepository tareaRepo;

    @Scheduled(cron = "0 10 * * * *", zone = "America/Lima")
    @SchedulerLock(name = "tareas_cierre_automatico_job", lockAtLeastFor = "30s", lockAtMostFor = "10m")
    @Transactional
    public void cerrarVencidas() {
        ejecutar();
    }

    public int ejecutar() {
        var vencidas = tareaRepo.vencidasSinTardia(LocalDateTime.now());
        for (Tarea t : vencidas) {
            t.setEstado("CERRADA");
            t.setActualizadaEn(LocalDateTime.now());
            tareaRepo.save(t);
        }
        if (!vencidas.isEmpty()) log.info("{} tarea(s) vencidas cerradas automáticamente", vencidas.size());
        return vencidas.size();
    }
}
