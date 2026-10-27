package pe.edu.upeu.eduspace.examenes.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upeu.eduspace.examenes.entity.Examen;
import pe.edu.upeu.eduspace.examenes.entity.Intento;
import pe.edu.upeu.eduspace.examenes.repository.ExamenRepository;
import pe.edu.upeu.eduspace.examenes.repository.IntentoRepository;
import pe.edu.upeu.eduspace.examenes.service.CalificacionAutomaticaService;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CierreExamenJob {

    private final ExamenRepository examenRepository;
    private final IntentoRepository intentoRepository;
    private final CalificacionAutomaticaService calificacionAutomaticaService;

    @Scheduled(fixedRate = 300000) // Cada 5 minutos
    @SchedulerLock(name = "examenes_cierre_examen_job", lockAtLeastFor = "30s", lockAtMostFor = "5m")
    @Transactional
    public void cerrarExamenesVencidos() {
        ejecutar();
    }

    @Transactional
    public int ejecutar() {
        LocalDateTime ahora = LocalDateTime.now();
        List<Examen> vencidos = examenRepository.findExamenesVencidosParaCierre(ahora);

        for (Examen e : vencidos) {
            log.info("Cerrando ventana de examen ID {} ('{}')", e.getId(), e.getTitulo());

            // Cerrar cualquier intento que haya quedado EN_CURSO
            List<Intento> abiertos = intentoRepository.findByExamenId(e.getId()).stream()
                .filter(i -> "EN_CURSO".equals(i.getEstado()))
                .toList();

            for (Intento i : abiertos) {
                i.setEstado("VENCIDO");
                intentoRepository.save(i);
                calificacionAutomaticaService.calificarIntento(i.getId());
            }

            e.setEstado("CERRADO");
            examenRepository.save(e);
        }
        return vencidos.size();
    }
}
