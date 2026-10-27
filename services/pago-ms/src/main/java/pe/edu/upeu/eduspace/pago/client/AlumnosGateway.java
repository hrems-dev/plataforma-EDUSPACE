package pe.edu.upeu.eduspace.pago.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import pe.edu.upeu.eduspace.pago.config.CacheConfig;

import java.util.Collections;
import java.util.List;

/** Acceso a alumnos-ms con circuit breaker (fallback) y caché corta; las lecturas de pago no dependen de que alumnos-ms esté arriba. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlumnosGateway {

    private final AlumnosClient alumnosClient;

    @CircuitBreaker(name = "alumnosClient", fallbackMethod = "responsableFallback")
    @Cacheable(value = CacheConfig.CACHE_RESPONSABLE_PAGO, key = "#alumnoId", unless = "#result == null")
    public AlumnoContactoResponse responsablePago(Long alumnoId) {
        return alumnosClient.getResponsablePago(alumnoId);
    }

    @CircuitBreaker(name = "alumnosClient", fallbackMethod = "hijosFallback")
    @Cacheable(value = CacheConfig.CACHE_HIJOS_APODERADO, key = "#usuarioId", unless = "#result == null || #result.isEmpty()")
    public List<AlumnoContactoResponse.HijoResumen> hijos(Long usuarioId) {
        return alumnosClient.getHijosPorUsuario(usuarioId);
    }

    AlumnoContactoResponse responsableFallback(Long alumnoId, Throwable t) {
        log.warn("alumnos-ms no disponible para responsable de {}: {}", alumnoId, t.toString());
        return null;
    }

    List<AlumnoContactoResponse.HijoResumen> hijosFallback(Long usuarioId, Throwable t) {
        log.warn("alumnos-ms no disponible para hijos de usuario {}: {}", usuarioId, t.toString());
        return Collections.emptyList();
    }
}
