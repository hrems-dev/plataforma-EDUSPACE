package pe.edu.upeu.eduspace.tareas.client;

import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import pe.edu.upeu.eduspace.tareas.config.CacheConfig;
import pe.edu.upeu.eduspace.tareas.exception.ResourceNotFoundException;
import pe.edu.upeu.eduspace.tareas.exception.ServicioNoDisponibleException;

/** planificacion-ms con circuit breaker y caché; el fallback de autorización es DENEGAR. */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanificacionGateway {

    private final PlanificacionClient client;

    @CircuitBreaker(name = "planificacionClient", fallbackMethod = "docenteFallback")
    @Cacheable(value = CacheConfig.CACHE_DOCENTE, key = "#usuarioId", unless = "#result == null")
    public PlanificacionClient.Docente docentePorUsuario(Long usuarioId) {
        try {
            return client.docentePorUsuario(usuarioId);
        } catch (FeignException.NotFound e) {
            return null;
        }
    }

    @CircuitBreaker(name = "planificacionClient", fallbackMethod = "validarFallback")
    @Cacheable(value = CacheConfig.CACHE_ASIGNACIONES, key = "#docenteId + ':' + #seccionId + ':' + #cursoId")
    public boolean asignacionValida(Long docenteId, Long seccionId, Long cursoId) {
        return Boolean.TRUE.equals(client.validarAsignacion(docenteId, seccionId, cursoId));
    }

    @CircuitBreaker(name = "planificacionClient", fallbackMethod = "seccionFallback")
    public PlanificacionClient.Seccion seccion(Long id) {
        try {
            return client.seccion(id);
        } catch (FeignException.NotFound e) {
            throw new ResourceNotFoundException("Sección no encontrada: " + id);
        }
    }

    @CircuitBreaker(name = "planificacionClient", fallbackMethod = "periodoFallback")
    @Cacheable(value = CacheConfig.CACHE_PERIODOS, key = "#id")
    public PlanificacionClient.Periodo periodo(Long id) {
        try {
            return client.periodo(id);
        } catch (FeignException.NotFound e) {
            throw new ResourceNotFoundException("Periodo no encontrado: " + id);
        }
    }

    /** Solo informativo (notificaciones): si falla devuelve null. */
    public String nombreCursoOpcional(Long cursoId) {
        try {
            PlanificacionClient.Curso c = client.curso(cursoId);
            return c == null ? null : c.nombre();
        } catch (Exception e) {
            log.debug("Sin nombre de curso {}: {}", cursoId, e.getMessage());
            return null;
        }
    }

    PlanificacionClient.Docente docenteFallback(Long usuarioId, Throwable t) { throw caido(t); }

    boolean validarFallback(Long docenteId, Long seccionId, Long cursoId, Throwable t) { throw caido(t); }

    PlanificacionClient.Seccion seccionFallback(Long id, Throwable t) { throw caido(t); }

    PlanificacionClient.Periodo periodoFallback(Long id, Throwable t) { throw caido(t); }

    private RuntimeException caido(Throwable t) {
        if (t instanceof ResourceNotFoundException r) return r;
        log.warn("planificacion-ms no disponible: {}", t.toString());
        return new ServicioNoDisponibleException("No se puede validar con planificacion-ms en este momento; intente nuevamente.");
    }
}
