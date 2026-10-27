package pe.edu.upeu.eduspace.calificaciones.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import pe.edu.upeu.eduspace.calificaciones.client.AlumnosClient;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Control de pertenencia: un alumno o apoderado solo ve datos de sus propios alumnos. */
@Service
@RequiredArgsConstructor
public class IdentidadService {

    private static final Set<String> ROLES_STAFF = Set.of("ADMIN", "SECRETARIA", "DOCENTE");

    private final AlumnosClient alumnosClient;

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

    public boolean esStaff(Jwt jwt) {
        return roles(jwt).stream().anyMatch(ROLES_STAFF::contains);
    }

    public Long usuarioId(Jwt jwt) {
        Object v = jwt == null ? null : jwt.getClaim("usuarioId");
        if (v instanceof Number n) return n.longValue();
        if (v instanceof String s && s.matches("\\d+")) return Long.parseLong(s);
        throw new AccessDeniedException("El token no contiene el claim usuarioId.");
    }

    /** Alumnos del usuario: él mismo (rol ALUMNO) y/o sus hijos (rol APODERADO). */
    public Set<Long> alumnosDelUsuario(Jwt jwt) {
        Set<Long> ids = new HashSet<>();
        Set<String> roles = roles(jwt);
        Long uid = usuarioId(jwt);
        if (roles.contains("ALUMNO")) {
            AlumnosClient.AlumnoUsuario a = null;
            try {
                a = alumnosClient.getAlumnoDeUsuario(uid);
            } catch (feign.FeignException.NotFound e) {
                // el usuario no es un alumno
            }
            if (a != null && a.alumnoId() != null) ids.add(a.alumnoId());
        }
        if (roles.contains("APODERADO")) {
            try {
                for (AlumnosClient.HijoResumen h : alumnosClient.getHijosPorUsuario(uid)) ids.add(h.getAlumnoId());
            } catch (feign.FeignException.NotFound e) {
                // el usuario no tiene hijos registrados
            }
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
