package pe.edu.upeu.eduspace.asistencia.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upeu.eduspace.asistencia.config.AsistenciaProperties;
import pe.edu.upeu.eduspace.asistencia.dto.Dtos.SesionResponse;
import pe.edu.upeu.eduspace.asistencia.entity.AsistenciaDetalle;
import pe.edu.upeu.eduspace.asistencia.entity.SesionAsistencia;
import pe.edu.upeu.eduspace.asistencia.repository.DetalleRepository;
import pe.edu.upeu.eduspace.asistencia.repository.SesionRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CierreSesionService {

    private final SesionService sesionService;
    private final SesionRepository sesionRepo;
    private final DetalleRepository detalleRepo;
    private final RegistroAsistenciaService registro;
    private final AsistenciaProperties props;

    /** Cierre manual por el docente dueño (o ADMIN). Cerrar una sesión ya cerrada es idempotente. */
    @Transactional
    public SesionResponse cerrar(Long sesionId, Long usuarioId, boolean esAdmin) {
        SesionAsistencia s = sesionService.obtener(sesionId);
        sesionService.exigirDocenteDe(s, usuarioId, esAdmin);
        cerrarInterno(s, usuarioId, false);
        return sesionService.hoja(sesionId);
    }

    /** Job nocturno: cierra las sesiones ABIERTAS de días anteriores. Devuelve cuántas cerró. */
    @Transactional
    public int cerrarVencidas() {
        List<SesionAsistencia> abiertas = sesionRepo.findByEstadoAndFechaBefore("ABIERTA", LocalDate.now());
        for (SesionAsistencia s : abiertas) {
            cerrarInterno(s, null, props.isMarcarFaltaSinRegistro());
        }
        if (!abiertas.isEmpty()) log.info("Cierre nocturno: {} sesión(es) cerradas", abiertas.size());
        return abiertas.size();
    }

    private void cerrarInterno(SesionAsistencia s, Long usuarioId, boolean faltaSiNoRegistro) {
        if ("CERRADA".equals(s.getEstado())) return;
        if (faltaSiNoRegistro) {
            for (AsistenciaDetalle d : detalleRepo.findBySesionIdOrderByIdAsc(s.getId())) {
                if (!Boolean.TRUE.equals(d.getConfirmado()) && "PRESENTE".equals(d.getEstado())) {
                    d.setEstado("FALTA");
                    d.setActualizadoEn(LocalDateTime.now());
                    d = detalleRepo.save(d);
                    registro.notificarFaltaSiCorresponde(s, d);
                }
            }
        }
        s.setEstado("CERRADA");
        s.setCerradaEn(LocalDateTime.now());
        s.setCerradaPor(usuarioId);
        sesionRepo.save(s);
    }
}
