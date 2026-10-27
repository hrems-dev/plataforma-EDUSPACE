package pe.edu.upeu.eduspace.examenes.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upeu.eduspace.examenes.entity.Intento;
import pe.edu.upeu.eduspace.examenes.repository.IntentoRepository;
import pe.edu.upeu.eduspace.examenes.service.CalificacionAutomaticaService;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CierreIntentosJob {

    private final IntentoRepository intentoRepository;
    private final CalificacionAutomaticaService calificacionAutomaticaService;

    @Scheduled(fixedRate = 60000)
    @SchedulerLock(name = "examenes_cierre_intentos_job", lockAtLeastFor = "15s", lockAtMostFor = "2m")
    @Transactional
    public void cerrarIntentosVencidos() {
        ejecutar();
    }

    /** Separado del @Scheduled (con lock de ShedLock) para poder invocarlo directamente. */
    @Transactional
    public int ejecutar() {
        LocalDateTime ahora = LocalDateTime.now();
        List<Intento> intentosVencidos = intentoRepository.findIntentosVencidosEnCurso(ahora);

        for (Intento i : intentosVencidos) {
            log.info("Cerrando automáticamente intento vencido ID {} del alumno {}", i.getId(), i.getAlumnoId());
            i.setEstado("VENCIDO");
            intentoRepository.save(i);
            calificacionAutomaticaService.calificarIntento(i.getId());
        }
        return intentosVencidos.size();
    }
}

