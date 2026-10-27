package pe.edu.eduspace.notificaciones.client;

import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Envuelve la llamada Feign con el circuit breaker {@code alumnosClient}. Es un bean distinto de quien lo invoca
 * ({@code ContactosAlumnoService}): la anotación solo actúa cuando la llamada cruza el proxy, no si se llama a un
 * método de la misma clase. El fallback devuelve "contactos no disponibles" para que el consumidor deje la
 * notificación PENDIENTE y se reintente en lugar de perderla.
 */
@Component
public class AlumnosGateway {

    private static final Logger log = LoggerFactory.getLogger(AlumnosGateway.class);

    private final AlumnosClient client;

    public AlumnosGateway(AlumnosClient client) {
        this.client = client;
    }

    @CircuitBreaker(name = "alumnosClient", fallbackMethod = "noDisponible")
    public ContactosResultado contactos(Long alumnoId) {
        try {
            return ContactosResultado.disponible(client.contactos(alumnoId));
        } catch (FeignException.NotFound ex) {
            // un 404 es una respuesta válida ("no existe"), no una falla del servicio: no abre el circuito
            return ContactosResultado.noExiste();
        }
    }

    ContactosResultado noDisponible(Long alumnoId, Throwable causa) {
        log.warn("component=AlumnosGateway eventType=Contactos key={} status=NO_DISPONIBLE cause={}", alumnoId,
                causa.getClass().getSimpleName());
        return ContactosResultado.noDisponible();
    }
}
