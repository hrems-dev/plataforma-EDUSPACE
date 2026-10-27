package pe.edu.upeu.eduspace.asistencia.client;

import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import pe.edu.upeu.eduspace.asistencia.config.CacheConfig;
import pe.edu.upeu.eduspace.asistencia.exception.ResourceNotFoundException;
import pe.edu.upeu.eduspace.asistencia.exception.ServicioNoDisponibleException;

import java.time.LocalDate;
import java.util.List;

/**
 * planificacion-ms con circuit breaker. Fallback de autorización = DENEGAR (nunca se asume permiso);
 * las lecturas de reportes no dependen de este servicio.
 */
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

    @CircuitBreaker(name = "planificacionClient", fallbackMethod = "asignacionesFallback")
    @Cacheable(value = CacheConfig.CACHE_ASIGNACIONES, key = "#usuarioId + ':' + #anioId")
    public List<PlanificacionClient.Asignacion> asignaciones(Long usuarioId, Long anioId) {
        try {
            return client.asignaciones(usuarioId, anioId);
        } catch (FeignException.NotFound e) {
            return List.of();
        }
    }

    @CircuitBreaker(name = "planificacionClient", fallbackMethod = "validarFallback")
    @Cacheable(value = CacheConfig.CACHE_ASIGNACIONES, key = "'v:' + #docenteId + ':' + #seccionId + ':' + #cursoId")
    public boolean asignacionValida(Long docenteId, Long seccionId, Long cursoId) {
        return Boolean.TRUE.equals(client.validarAsignacion(docenteId, seccionId, cursoId));
    }

    @CircuitBreaker(name = "planificacionClient", fallbackMethod = "habilFallback")
    @Cacheable(value = CacheConfig.CACHE_FERIADOS, key = "#fecha")
    public boolean esHabil(LocalDate fecha) {
        return Boolean.TRUE.equals(client.esHabil(fecha));
    }

    @CircuitBreaker(name = "planificacionClient", fallbackMethod = "seccionFallback")
    public PlanificacionClient.Seccion seccion(Long id) {
        try {
            return client.seccion(id);
        } catch (FeignException.NotFound e) {
            throw new ResourceNotFoundException("Sección no encontrada: " + id);
        }
    }

    /** Periodo que contiene la fecha, o null si no hay (la sesión queda sin periodo). */
    public Long periodoDeFecha(Long anioId, LocalDate fecha) {
        try {
            return client.periodos(anioId).stream()
                .filter(p -> p.fechaInicio() != null && p.fechaFin() != null
                    && !fecha.isBefore(p.fechaInicio()) && !fecha.isAfter(p.fechaFin()))
                .map(PlanificacionClient.Periodo::id).findFirst().orElse(null);
        } catch (Exception e) {
            log.warn("No se pudo resolver el periodo de {}: {}", fecha, e.getMessage());
            return null;
        }
    }

    @CircuitBreaker(name = "planificacionClient", fallbackMethod = "periodoFallback")
    public PlanificacionClient.Periodo periodo(Long id) {
        try {
            return client.periodo(id);
        } catch (FeignException.NotFound e) {
            throw new ResourceNotFoundException("Periodo no encontrado: " + id);
        }
    }

    PlanificacionClient.Docente docenteFallback(Long usuarioId, Throwable t) { throw caido(t); }

    List<PlanificacionClient.Asignacion> asignacionesFallback(Long usuarioId, Long anioId, Throwable t) { throw caido(t); }

    boolean validarFallback(Long docenteId, Long seccionId, Long cursoId, Throwable t) { throw caido(t); }

    boolean habilFallback(LocalDate fecha, Throwable t) { throw caido(t); }

    PlanificacionClient.Seccion seccionFallback(Long id, Throwable t) { throw caido(t); }

    PlanificacionClient.Periodo periodoFallback(Long id, Throwable t) { throw caido(t); }

    private RuntimeException caido(Throwable t) {
        if (t instanceof ResourceNotFoundException r) return r;
        log.warn("planificacion-ms no disponible: {}", t.toString());
        return new ServicioNoDisponibleException("No se puede validar con planificacion-ms en este momento; intente nuevamente.");
    }
}
