package pe.edu.upeu.eduspace.examenes.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upeu.eduspace.examenes.repository.EventoProcesadoRepository;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class LimpiezaJob {

    private final EventoProcesadoRepository eventoProcesadoRepository;

    @Scheduled(cron = "0 0 3 * * *", zone = "America/Lima")
    @SchedulerLock(name = "examenes_limpieza_job", lockAtLeastFor = "15s", lockAtMostFor = "10m")
    @Transactional
    public void limpiarTablasTecnicas() {
        LocalDateTime limite = LocalDateTime.now().minusDays(30);
        long eliminados = eventoProcesadoRepository.deleteByProcesadoEnBefore(limite);
        log.info("Limpieza técnica en examenes-ms: {} eventos procesados eliminados con más de 30 días.", eliminados);
    }
}

