package pe.edu.upeu.eduspace.calificaciones.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upeu.eduspace.calificaciones.repository.EventoProcesadoRepository;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class LimpiezaJob {

    private final EventoProcesadoRepository eventoProcesadoRepository;

    @Scheduled(cron = "0 0 3 * * ?", zone = "America/Lima")
    @SchedulerLock(name = "calificaciones_limpieza_eventos_procesados", lockAtLeastFor = "1m", lockAtMostFor = "15m")
    @Transactional
    public void limpiarEventosProcesadosAntiguos() {
        LocalDateTime limite = LocalDateTime.now().minusDays(30);
        int eliminados = eventoProcesadoRepository.deleteAntiguos(limite);
        if (eliminados > 0) {
            log.info("Limpieza periódica en calificaciones-ms: se eliminaron {} eventos procesados antiguos (> 30 días)", eliminados);
        }
    }
}

