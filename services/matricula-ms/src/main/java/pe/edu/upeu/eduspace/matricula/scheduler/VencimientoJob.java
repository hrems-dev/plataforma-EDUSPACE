package pe.edu.upeu.eduspace.matricula.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pe.edu.upeu.eduspace.matricula.entity.Matricula;
import pe.edu.upeu.eduspace.matricula.repository.MatriculaRepository;
import pe.edu.upeu.eduspace.matricula.service.MatriculaService;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class VencimientoJob {

    private final MatriculaRepository repo;
    private final MatriculaService service;

    @Scheduled(cron = "0 0 * * * *", zone = "America/Lima")
    @SchedulerLock(name = "matricula_vencimiento_job", lockAtLeastFor = "30s", lockAtMostFor = "10m")
    public void anularVencidas() {
        ejecutar();
    }

    /** Separado del @Scheduled para poder probarlo directamente. */
    public int ejecutar() {
        List<Matricula> vencidas = repo.findPreMatriculasVencidas(LocalDateTime.now());
        int n = 0;
        for (Matricula m : vencidas) {
            try {
                service.anularPorVencimiento(m.getId());
                n++;
            } catch (Exception e) {
                log.error("No se pudo anular la matrícula vencida {}: {}", m.getId(), e.getMessage());
            }
        }
        if (n > 0) log.info("{} pre-matrícula(s) vencidas anuladas", n);
        return n;
    }
}
