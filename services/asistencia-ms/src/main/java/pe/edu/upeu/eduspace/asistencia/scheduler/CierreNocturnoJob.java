package pe.edu.upeu.eduspace.asistencia.scheduler;

import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pe.edu.upeu.eduspace.asistencia.service.CierreSesionService;

@Component
@RequiredArgsConstructor
public class CierreNocturnoJob {

    private final CierreSesionService service;

    @Scheduled(cron = "0 30 23 * * *", zone = "America/Lima")
    @SchedulerLock(name = "asistencia_cierre_nocturno", lockAtLeastFor = "30s", lockAtMostFor = "10m")
    public void cerrarSesionesDeDiasAnteriores() {
        service.cerrarVencidas();
    }
}
