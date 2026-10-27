package pe.edu.upeu.eduspace.tareas.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import pe.edu.upeu.eduspace.tareas.client.AlumnosClient;
import pe.edu.upeu.eduspace.tareas.client.AlumnosGateway;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Traduce el usuarioId del JWT a los alumnos que el usuario puede ver (él mismo o sus hijos). */
@Service
@RequiredArgsConstructor
public class IdentidadService {

    private static final Set<String> STAFF = Set.of("ADMIN", "SECRETARIA", "DOCENTE");

    private final AlumnosGateway alumnos;

    public Set<String> roles(Jwt jwt) {
        Set<String> roles = new HashSet<>();
        if (jwt == null) return roles;
        if (jwt.getClaim("realm_access") instanceof Map<?, ?> m && m.get("roles") instanceof List<?> l) {
            l.forEach(r -> roles.add(r.toString().toUpperCase().replace("ROLE_", "")));
        }
        List<String> directos = jwt.getClaimAsStringList("roles");
        if (directos != null) directos.forEach(r -> roles.add(r.toUpperCase().replace("ROLE_", "")));
        return roles;
    }

    public boolean esAdmin(Jwt jwt) {
        return roles(jwt).contains("ADMIN");
    }

    public boolean esDocente(Jwt jwt) {
        return roles(jwt).contains("DOCENTE");
    }

    public boolean esStaff(Jwt jwt) {
        return roles(jwt).stream().anyMatch(STAFF::contains);
    }

    public Long usuarioId(Jwt jwt) {
        Object v = jwt == null ? null : jwt.getClaim("usuarioId");
        if (v instanceof Number n) return n.longValue();
        if (v instanceof String s && s.matches("\\d+")) return Long.parseLong(s);
        throw new AccessDeniedException("El token no contiene el claim usuarioId.");
    }

    public Set<Long> alumnosDelUsuario(Jwt jwt) {
        Set<Long> ids = new HashSet<>();
        Set<String> roles = roles(jwt);
        Long uid = usuarioId(jwt);
        if (roles.contains("ALUMNO")) {
            Long a = alumnos.alumnoIdDeUsuario(uid);
            if (a != null) ids.add(a);
        }
        if (roles.contains("APODERADO")) {
            for (AlumnosClient.Hijo h : alumnos.hijosDeUsuario(uid)) ids.add(h.alumnoId());
        }
        return ids;
    }

    public void exigirAcceso(Jwt jwt, Long alumnoId) {
        if (esStaff(jwt)) return;
        if (!alumnosDelUsuario(jwt).contains(alumnoId)) {
            throw new AccessDeniedException("No tiene acceso a la información de este alumno.");
        }
    }
}
