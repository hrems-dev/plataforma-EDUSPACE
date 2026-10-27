package pe.edu.upeu.eduspace.pago.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upeu.eduspace.pago.client.AlumnoContactoResponse;
import pe.edu.upeu.eduspace.pago.client.AlumnosGateway;
import pe.edu.upeu.eduspace.pago.entity.MatriculaLocal;
import pe.edu.upeu.eduspace.pago.repository.MatriculaLocalRepository;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ResponsableJob {

    private final MatriculaLocalRepository matriculaLocalRepository;
    private final AlumnosGateway alumnosGateway;

    @Scheduled(cron = "0 0 2 * * *", zone = "America/Lima")
    @SchedulerLock(name = "pago_responsable_job", lockAtLeastFor = "15s", lockAtMostFor = "10m")
    @Transactional
    public void completarResponsablesFaltantes() {
        List<MatriculaLocal> sinResponsable = matriculaLocalRepository.findAll().stream()
            .filter(m -> m.getResponsableUsuarioId() == null)
            .toList();

        for (MatriculaLocal m : sinResponsable) {
            try {
                AlumnoContactoResponse resp = alumnosGateway.responsablePago(m.getAlumnoId());
                if (resp != null && resp.getUsuarioId() != null) {
                    m.setResponsableUsuarioId(resp.getUsuarioId());
                    m.setResponsableNombre(resp.getNombre());
                    matriculaLocalRepository.save(m);
                    log.info("Completado responsable para alumno {}: {}", m.getAlumnoId(), resp.getNombre());
                }
            } catch (Exception e) {
                log.warn("No se pudo obtener responsable para alumno {}: {}", m.getAlumnoId(), e.getMessage());
            }
        }
    }
}

