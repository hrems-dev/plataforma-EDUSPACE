package pe.edu.upeu.eduspace.pago.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upeu.eduspace.pago.repository.EventoProcesadoRepository;
import pe.edu.upeu.eduspace.pago.repository.OutboxEventoRepository;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class LimpiezaJob {

    private final EventoProcesadoRepository eventoProcesadoRepository;
    private final OutboxEventoRepository outboxEventoRepository;

    @Scheduled(cron = "0 0 3 * * *", zone = "America/Lima")
    @SchedulerLock(name = "pago_limpieza_job", lockAtLeastFor = "15s", lockAtMostFor = "10m")
    @Transactional
    public void limpiarTablasTecnicas() {
        LocalDateTime limite = LocalDateTime.now().minusDays(30);
        long eliminadosEventos = eventoProcesadoRepository.deleteByProcesadoEnBefore(limite);
        long eliminadosOutbox = outboxEventoRepository.deleteByEnviadoEnBefore(limite);
        log.info("Limpieza técnica completada: {} eventos procesados y {} outbox eliminados con más de 30 días.",
            eliminadosEventos, eliminadosOutbox);
    }
}

