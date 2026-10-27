package pe.edu.upeu.eduspace.matricula.client;

import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pe.edu.upeu.eduspace.matricula.exception.ResourceNotFoundException;
import pe.edu.upeu.eduspace.matricula.exception.ServicioNoDisponibleException;

import java.util.Collections;
import java.util.List;

/** alumnos-ms con circuit breaker: sin validar al alumno no se puede matricular (503). */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlumnosGateway {

    private final AlumnosClient client;

    @CircuitBreaker(name = "alumnosClient", fallbackMethod = "alumnoFallback")
    public AlumnosClient.AlumnoBasico alumno(Long id) {
        try {
            return client.alumno(id);
        } catch (FeignException.NotFound e) {
            throw new ResourceNotFoundException("Alumno no encontrado: " + id);
        }
    }

    @CircuitBreaker(name = "alumnosClient", fallbackMethod = "expedienteFallback")
    public AlumnosClient.Expediente expediente(Long id) {
        try {
            return client.expediente(id);
        } catch (FeignException.NotFound e) {
            throw new ResourceNotFoundException("Alumno no encontrado: " + id);
        }
    }

    /** El responsable de pago es informativo: si falta o alumnos-ms cae, se matricula igual (pago-ms lo completa luego). */
    public AlumnosClient.ResponsablePago responsablePagoOpcional(Long id) {
        try {
            return client.responsablePago(id);
        } catch (Exception e) {
            log.warn("Sin responsable de pago para alumno {}: {}", id, e.getMessage());
            return null;
        }
    }

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
    public List<AlumnosClient.Hijo> hijosDeUsuario(Long usuarioId) {
        try {
            return client.hijosDeUsuario(usuarioId);
        } catch (FeignException.NotFound e) {
            return Collections.emptyList();
        }
    }

    AlumnosClient.AlumnoBasico alumnoFallback(Long id, Throwable t) {
        throw noDisponible("alumnos-ms", t);
    }

    AlumnosClient.Expediente expedienteFallback(Long id, Throwable t) {
        throw noDisponible("alumnos-ms", t);
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
