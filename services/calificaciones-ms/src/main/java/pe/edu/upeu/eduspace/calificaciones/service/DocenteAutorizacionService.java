package pe.edu.upeu.eduspace.calificaciones.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import pe.edu.upeu.eduspace.calificaciones.client.PlanificacionClient;
import pe.edu.upeu.eduspace.calificaciones.exception.BusinessRuleException;

/**
 * El docente solo califica en las secciones y cursos que tiene asignados. Si planificacion-ms no responde se
 * DENIEGA (nunca se asume permiso); ADMIN no está sujeto a asignación.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocenteAutorizacionService {

    private final PlanificacionClient planificacionClient;

    public void exigirAsignacion(Long usuarioId, boolean esAdmin, Long seccionId, Long cursoId) {
        if (esAdmin) return;
        if (usuarioId == null) throw new AccessDeniedException("No se pudo identificar al docente.");
        PlanificacionClient.DocenteInterno docente;
        try {
            docente = planificacionClient.getDocentePorUsuario(usuarioId);
        } catch (feign.FeignException.NotFound e) {
            throw new AccessDeniedException("El usuario no está registrado como docente.");
        } catch (Exception e) {
            log.warn("planificacion-ms no disponible al validar docente: {}", e.getMessage());
            throw new BusinessRuleException("No se puede validar la asignación docente en este momento; intente nuevamente.");
        }
        if (docente == null || docente.docenteId() == null) {
            throw new AccessDeniedException("El usuario no está registrado como docente.");
        }
        boolean asignado;
        try {
            asignado = Boolean.TRUE.equals(planificacionClient.validarAsignacionDocente(docente.docenteId(), seccionId, cursoId));
        } catch (Exception e) {
            log.warn("planificacion-ms no disponible al validar asignación: {}", e.getMessage());
            throw new BusinessRuleException("No se puede validar la asignación docente en este momento; intente nuevamente.");
        }
        if (!asignado) {
            throw new AccessDeniedException("El docente no tiene asignación para este curso y sección.");
        }
    }
}
