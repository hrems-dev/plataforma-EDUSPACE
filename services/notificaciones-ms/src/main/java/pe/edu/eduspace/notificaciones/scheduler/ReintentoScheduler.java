package pe.edu.eduspace.notificaciones.scheduler;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pe.edu.eduspace.notificaciones.service.ReintentoService;

/**
 * Tareas periódicas. Con varias réplicas de este servicio, ShedLock (tabla {@code shedlock}) hace que cada ciclo
 * lo ejecute una sola.
 */
@Component
public class ReintentoScheduler {

    private final ReintentoService servicio;

    public ReintentoScheduler(ReintentoService servicio) {
        this.servicio = servicio;
    }

    @Scheduled(fixedDelayString = "${app.reintentos.intervalo:PT1M}", initialDelayString = "${app.reintentos.espera-inicial:PT30S}")
    @SchedulerLock(name = "reintentoScheduler", lockAtMostFor = "PT5M", lockAtLeastFor = "PT10S")
    public void reintentar() {
        servicio.procesarPendientes();
    }

    @Scheduled(cron = "${app.limpieza.cron:0 30 3 * * *}")
    @SchedulerLock(name = "limpiezaEventosProcesados", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    public void limpiar() {
        servicio.limpiarEventosProcesados();
    }
}
