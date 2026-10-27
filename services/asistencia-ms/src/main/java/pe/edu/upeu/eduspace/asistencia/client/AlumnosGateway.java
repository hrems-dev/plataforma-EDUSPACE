package pe.edu.upeu.eduspace.asistencia.client;

import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pe.edu.upeu.eduspace.asistencia.exception.ResourceNotFoundException;
import pe.edu.upeu.eduspace.asistencia.exception.ServicioNoDisponibleException;

import java.util.Collections;
import java.util.List;

/** alumnos-ms con circuit breaker: sin validar al alumno no se puede matricular (503). */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlumnosGateway {

    private final AlumnosClient client;

    @CircuitBreaker(name = "alumnosClient", fallbackMethod = "alumnoUsuarioFallback")
    public Long alumnoIdDeUsuario(Long usuarioId) {
        try {
            AlumnosClient.AlumnoUsuario a = client.alumnoDeUsuario(usuarioId);
            return a == null ? null : a.alumnoId();
        } catch (FeignException.NotFound e) {
            return null;
        }
    }

    @CircuitBreaker(name = "alumnosClient", fallbackMethod = "hijosFallback")
    @org.springframework.cache.annotation.Cacheable(value = pe.edu.upeu.eduspace.asistencia.config.CacheConfig.CACHE_HIJOS, key = "#usuarioId")
    public List<AlumnosClient.Hijo> hijosDeUsuario(Long usuarioId) {
        try {
            return client.hijosDeUsuario(usuarioId);
        } catch (FeignException.NotFound e) {
            return Collections.emptyList();
        }
    }

    Long alumnoUsuarioFallback(Long usuarioId, Throwable t) {
        throw noDisponible("alumnos-ms", t);
    }

    List<AlumnosClient.Hijo> hijosFallback(Long usuarioId, Throwable t) {
        throw noDisponible("alumnos-ms", t);
    }

    private RuntimeException noDisponible(String servicio, Throwable t) {
        if (t instanceof ResourceNotFoundException r) return r;
        log.warn("{} no disponible: {}", servicio, t.toString());
        return new ServicioNoDisponibleException("No se puede validar con " + servicio + " en este momento; intente nuevamente.");
    }
}
