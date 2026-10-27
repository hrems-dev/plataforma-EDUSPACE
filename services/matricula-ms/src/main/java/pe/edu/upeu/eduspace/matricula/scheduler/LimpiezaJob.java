package pe.edu.upeu.eduspace.matricula.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upeu.eduspace.matricula.repository.EventoProcesadoRepository;
import pe.edu.upeu.eduspace.matricula.repository.OutboxEventoRepository;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class LimpiezaJob {

    private final EventoProcesadoRepository eventoRepo;
    private final OutboxEventoRepository outboxRepo;

    @Scheduled(cron = "0 0 3 * * *", zone = "America/Lima")
    @SchedulerLock(name = "matricula_limpieza_job", lockAtLeastFor = "15s", lockAtMostFor = "10m")
    @Transactional
    public void limpiar() {
        LocalDateTime limite = LocalDateTime.now().minusDays(30);
        long e = eventoRepo.deleteByProcesadoEnBefore(limite);
        long o = outboxRepo.deleteByEnviadoEnBefore(limite);
        log.info("Limpieza técnica: {} eventos procesados y {} outbox enviados eliminados.", e, o);
    }
}
