package pe.edu.upeu.eduspace.asistencia.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upeu.eduspace.asistencia.client.PlanificacionClient;
import pe.edu.upeu.eduspace.asistencia.client.PlanificacionGateway;
import pe.edu.upeu.eduspace.asistencia.config.AsistenciaProperties;
import pe.edu.upeu.eduspace.asistencia.dto.Dtos.*;
import pe.edu.upeu.eduspace.asistencia.entity.AsistenciaDetalle;
import pe.edu.upeu.eduspace.asistencia.entity.MatriculaLocal;
import pe.edu.upeu.eduspace.asistencia.exception.BusinessRuleException;
import pe.edu.upeu.eduspace.asistencia.repository.DetalleRepository;
import pe.edu.upeu.eduspace.asistencia.repository.MatriculaLocalRepository;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/** Reportes: solo leen la BD local (siguen disponibles aunque planificacion-ms o alumnos-ms estén caídos). */
@Service
@RequiredArgsConstructor
public class ReporteService {

    private final DetalleRepository detalleRepo;
    private final MatriculaLocalRepository matriculaRepo;
    private final PlanificacionGateway planificacion;
    private final AsistenciaProperties props;

    @Transactional(readOnly = true)
    public ResumenAlumnoResponse resumenAlumno(Long alumnoId, Long periodoId, LocalDate desde, LocalDate hasta) {
        LocalDate d = desde, h = hasta;
        if (periodoId != null) {
            PlanificacionClient.Periodo p = planificacion.periodo(periodoId);
            d = p.fechaInicio();
            h = p.fechaFin();
        }
        if (d == null) d = LocalDate.now().withDayOfYear(1);
        if (h == null) h = LocalDate.now();
        if (h.isBefore(d)) throw new BusinessRuleException("El rango de fechas es inválido.");
        List<AsistenciaDetalle> detalles = detalleRepo.delAlumnoEnRango(alumnoId, d, h);
        return new ResumenAlumnoResponse(alumnoId, d, h, periodoId,
            contar(detalles.stream().filter(x -> "DIA".equals(x.getSesion().getTipo())).toList()),
            contar(detalles.stream().filter(x -> "CURSO".equals(x.getSesion().getTipo())).toList()));
    }

    @Transactional(readOnly = true)
    public ReporteSeccionResponse reporteSeccion(Long seccionId, LocalDate desde, LocalDate hasta) {
        LocalDate d = desde != null ? desde : LocalDate.now().withDayOfYear(1);
        LocalDate h = hasta != null ? hasta : LocalDate.now();
        if (h.isBefore(d)) throw new BusinessRuleException("El rango de fechas es inválido.");
        Map<Long, List<AsistenciaDetalle>> porMatricula = detalleRepo.deSeccionEnRango(seccionId, d, h).stream()
            .filter(x -> "DIA".equals(x.getSesion().getTipo()))
            .collect(Collectors.groupingBy(AsistenciaDetalle::getMatriculaId));
        Map<Long, MatriculaLocal> matriculas = matriculaRepo.findBySeccionId(seccionId).stream()
            .filter(m -> "MATRICULADO".equals(m.getEstado()))
            .collect(Collectors.toMap(MatriculaLocal::getMatriculaId, m -> m));
        List<FilaReporte> filas = new ArrayList<>();
        for (MatriculaLocal m : matriculas.values()) {
            ConteoAsistencia c = contar(porMatricula.getOrDefault(m.getMatriculaId(), List.of()));
            boolean exceso = c.sesiones() > 0 && (c.faltas() * 100.0 / c.sesiones()) > props.getLimiteInasistenciaPorcentaje();
            filas.add(new FilaReporte(m.getAlumnoId(), m.getMatriculaId(), m.getAlumnoNombre(), c, exceso));
        }
        filas.sort(Comparator.comparing(FilaReporte::alumnoNombre));
        return new ReporteSeccionResponse(seccionId, d, h, filas.size(), filas, filas.stream().filter(FilaReporte::excesoFaltas).toList());
    }

    @Transactional(readOnly = true)
    public List<AsistenciaHijoResponse> deAlumnos(Set<Long> alumnoIds) {
        if (alumnoIds.isEmpty()) return List.of();
        LocalDate h = LocalDate.now(), d = h.minusDays(30);
        Map<Long, String> nombres = matriculaRepo.findByAlumnoIdIn(alumnoIds).stream()
            .collect(Collectors.toMap(MatriculaLocal::getAlumnoId, MatriculaLocal::getAlumnoNombre, (a, b) -> a));
        List<AsistenciaHijoResponse> res = new ArrayList<>();
        for (Long alumnoId : alumnoIds) {
            ResumenAlumnoResponse resumen = resumenAlumno(alumnoId, null, h.withDayOfYear(1), h);
            List<DetalleDia> ultimos = detalleRepo.delAlumnoEnRango(alumnoId, d, h).stream()
                .map(x -> new DetalleDia(x.getSesion().getFecha(), x.getSesion().getTipo(), x.getSesion().getCursoId(), x.getEstado(), x.getMinutosTarde()))
                .toList();
            res.add(new AsistenciaHijoResponse(alumnoId, nombres.getOrDefault(alumnoId, "Alumno " + alumnoId), resumen, ultimos));
        }
        return res;
    }

    /** Presente/tardanza/justificado cuentan como asistencia; FALTA es inasistencia injustificada. */
    static ConteoAsistencia contar(List<AsistenciaDetalle> detalles) {
        int presentes = 0, faltas = 0, tardanzas = 0, justificadas = 0;
        for (AsistenciaDetalle x : detalles) {
            switch (x.getEstado()) {
                case "PRESENTE" -> presentes++;
                case "FALTA" -> faltas++;
                case "TARDANZA" -> tardanzas++;
                case "JUSTIFICADO" -> justificadas++;
                default -> { }
            }
        }
        int total = detalles.size();
        double pct = total == 0 ? 0 : Math.round((presentes + tardanzas + justificadas) * 10000.0 / total) / 100.0;
        return new ConteoAsistencia(total, presentes, faltas, tardanzas, justificadas, pct);
    }
}
