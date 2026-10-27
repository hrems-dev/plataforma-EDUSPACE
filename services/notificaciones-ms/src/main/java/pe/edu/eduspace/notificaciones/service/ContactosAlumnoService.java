package pe.edu.eduspace.notificaciones.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Service;
import pe.edu.eduspace.notificaciones.client.AlumnosGateway;
import pe.edu.eduspace.notificaciones.client.ContactosDto;
import pe.edu.eduspace.notificaciones.client.ContactosResultado;
import pe.edu.eduspace.notificaciones.config.AppProperties;

/**
 * Contactos de un alumno con caché local de vencimiento corto (adenda A6): cuando alumnos-ms está caído se sirven
 * desde la caché; sin caché, el resultado es "no disponible" y la notificación queda PENDIENTE.
 */
@Service
public class ContactosAlumnoService {

    private final AlumnosGateway gateway;
    private final Cache<Long, ContactosDto> cache;

    public ContactosAlumnoService(AlumnosGateway gateway, AppProperties props) {
        this.gateway = gateway;
        this.cache = Caffeine.newBuilder().expireAfterWrite(props.contactos().cacheTtl())
                .maximumSize(props.contactos().cacheMaxSize()).build();
    }

    public ContactosResultado resolver(Long alumnoId) {
        ContactosDto enCache = cache.getIfPresent(alumnoId);
        if (enCache != null) {
            return ContactosResultado.disponible(enCache);
        }
        ContactosResultado resultado = gateway.contactos(alumnoId);
        if (resultado.estado() == ContactosResultado.Estado.DISPONIBLE) {
            cache.put(alumnoId, resultado.datos());
        }
        return resultado;
    }

    /** Solo para pruebas y diagnóstico. */
    public void vaciarCache() {
        cache.invalidateAll();
    }
}
