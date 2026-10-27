package pe.edu.upeu.eduspace.matricula.client;

import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import pe.edu.upeu.eduspace.matricula.config.CacheConfig;
import pe.edu.upeu.eduspace.matricula.exception.ResourceNotFoundException;
import pe.edu.upeu.eduspace.matricula.exception.ServicioNoDisponibleException;

import java.util.List;

/** planificacion-ms con circuit breaker y caché corta (sección, año activo, grado). */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanificacionGateway {

    private final PlanificacionClient client;

    @CircuitBreaker(name = "planificacionClient", fallbackMethod = "seccionFallback")
    @Cacheable(value = CacheConfig.CACHE_SECCION, key = "#id")
    public PlanificacionClient.Seccion seccion(Long id) {
        try {
            return client.seccion(id);
        } catch (FeignException.NotFound e) {
            throw new ResourceNotFoundException("Sección no encontrada: " + id);
        }
    }

    @CircuitBreaker(name = "planificacionClient", fallbackMethod = "seccionesFallback")
    public List<PlanificacionClient.Seccion> secciones(Long anioId, Long gradoId) {
        return client.secciones(anioId, gradoId);
    }

    @CircuitBreaker(name = "planificacionClient", fallbackMethod = "anioActivoFallback")
    @Cacheable(value = CacheConfig.CACHE_ANIO_ACTIVO, key = "'activo'")
    public PlanificacionClient.Anio anioActivo() {
        try {
            return client.anioActivo();
        } catch (FeignException.NotFound e) {
            throw new ResourceNotFoundException("No hay un año académico activo.");
        }
    }

    @CircuitBreaker(name = "planificacionClient", fallbackMethod = "anioFallback")
    @Cacheable(value = CacheConfig.CACHE_ANIO_ACTIVO, key = "#id")
    public PlanificacionClient.Anio anio(Long id) {
        try {
            return client.anio(id);
        } catch (FeignException.NotFound e) {
            throw new ResourceNotFoundException("Año académico no encontrado: " + id);
        }
    }

    @CircuitBreaker(name = "planificacionClient", fallbackMethod = "gradoFallback")
    @Cacheable(value = CacheConfig.CACHE_GRADO, key = "#id")
    public PlanificacionClient.Grado grado(Long id) {
        try {
            return client.grado(id);
        } catch (FeignException.NotFound e) {
            throw new ResourceNotFoundException("Grado no encontrado: " + id);
        }
    }

    PlanificacionClient.Seccion seccionFallback(Long id, Throwable t) { throw noDisponible(t); }

    List<PlanificacionClient.Seccion> seccionesFallback(Long anioId, Long gradoId, Throwable t) { throw noDisponible(t); }

    PlanificacionClient.Anio anioActivoFallback(Throwable t) { throw noDisponible(t); }

    PlanificacionClient.Anio anioFallback(Long id, Throwable t) { throw noDisponible(t); }

    PlanificacionClient.Grado gradoFallback(Long id, Throwable t) { throw noDisponible(t); }

    private RuntimeException noDisponible(Throwable t) {
        if (t instanceof ResourceNotFoundException r) return r;
        log.warn("planificacion-ms no disponible: {}", t.toString());
        return new ServicioNoDisponibleException("No se puede validar con planificacion-ms en este momento; intente nuevamente.");
    }
}
