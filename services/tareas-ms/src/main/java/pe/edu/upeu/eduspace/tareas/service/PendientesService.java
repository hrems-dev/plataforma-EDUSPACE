package pe.edu.upeu.eduspace.tareas.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upeu.eduspace.tareas.dto.Dtos.TareaPendienteResponse;
import pe.edu.upeu.eduspace.tareas.entity.Entrega;
import pe.edu.upeu.eduspace.tareas.entity.MatriculaLocal;
import pe.edu.upeu.eduspace.tareas.entity.Tarea;
import pe.edu.upeu.eduspace.tareas.repository.EntregaRepository;
import pe.edu.upeu.eduspace.tareas.repository.MatriculaLocalRepository;
import pe.edu.upeu.eduspace.tareas.repository.TareaRepository;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/** Tareas del alumno y de los hijos del apoderado (todas, o solo las pendientes). */
@Service
@RequiredArgsConstructor
public class PendientesService {

    private final TareaRepository tareaRepo;
    private final EntregaRepository entregaRepo;
    private final MatriculaLocalRepository matriculaRepo;

    @Transactional(readOnly = true)
    public List<TareaPendienteResponse> pendientes(Set<Long> alumnoIds) {
        return calcular(alumnoIds, true);
    }

    @Transactional(readOnly = true)
    public List<TareaPendienteResponse> todas(Set<Long> alumnoIds) {
        return calcular(alumnoIds, false);
    }

    private List<TareaPendienteResponse> calcular(Set<Long> alumnoIds, boolean soloPendientes) {
        if (alumnoIds.isEmpty()) return List.of();
        List<MatriculaLocal> matriculas = matriculaRepo.findByAlumnoIdIn(alumnoIds).stream()
            .filter(m -> "MATRICULADO".equals(m.getEstado())).toList();
        if (matriculas.isEmpty()) return List.of();
        Map<Long, List<MatriculaLocal>> porSeccion = matriculas.stream().collect(Collectors.groupingBy(MatriculaLocal::getSeccionId));
        Map<String, Entrega> entregas = new HashMap<>();
        for (Entrega e : entregaRepo.findByAlumnoIdIn(alumnoIds)) entregas.put(e.getTareaId() + ":" + e.getAlumnoId(), e);

        LocalDateTime ahora = LocalDateTime.now();
        List<Tarea> tareas = soloPendientes
            ? tareaRepo.findBySeccionIdInAndEstadoOrderByFechaEntregaAsc(porSeccion.keySet(), "PUBLICADA")
            : tareaRepo.findBySeccionIdInAndEstadoInOrderByFechaEntregaDesc(porSeccion.keySet(), List.of("PUBLICADA", "CERRADA"));
        List<TareaPendienteResponse> res = new ArrayList<>();
        for (Tarea t : tareas) {
            for (MatriculaLocal m : porSeccion.getOrDefault(t.getSeccionId(), List.of())) {
                Entrega e = entregas.get(t.getId() + ":" + m.getAlumnoId());
                String estado = e == null ? "SIN_ENTREGAR" : e.getEstado();
                boolean pendiente = e == null || "DEVUELTA".equals(e.getEstado());
                if (soloPendientes && !pendiente) continue;
                boolean vencida = ahora.isAfter(t.getFechaEntrega());
                if (soloPendientes && vencida && !Boolean.TRUE.equals(t.getPermiteTardia())) continue;
                res.add(new TareaPendienteResponse(t.getId(), t.getTitulo(), t.getCursoId(), t.getFechaEntrega(), vencida,
                    t.getPermiteTardia(), m.getAlumnoId(), m.getAlumnoNombre(), estado));
            }
        }
        return res;
    }
}
