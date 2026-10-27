package pe.edu.upeu.eduspace.examenes.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import pe.edu.upeu.eduspace.examenes.client.AlumnosClient;
import pe.edu.upeu.eduspace.examenes.client.PlanificacionClient;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Traduce la identidad del JWT (usuarioId de auth-ms) a las identidades de dominio
 * (docenteId de planificación, alumnoId de alumnos). Nunca se asume usuarioId == alumnoId.
 */
@Service
@RequiredArgsConstructor
public class IdentidadService {

    private final PlanificacionClient planificacionClient;
    private final AlumnosClient alumnosClient;

    public Long usuarioId(Jwt jwt) {
        Object v = jwt == null ? null : jwt.getClaim("usuarioId");
        if (v instanceof Number n) return n.longValue();
        if (v instanceof String s && s.matches("\\d+")) return Long.parseLong(s);
        throw new AccessDeniedException("El token no contiene el claim usuarioId.");
    }

    public Set<String> roles(Jwt jwt) {
        Set<String> roles = new HashSet<>();
        if (jwt == null) return roles;
        Object ra = jwt.getClaim("realm_access");
        if (ra instanceof Map<?, ?> m && m.get("roles") instanceof List<?> l) {
            l.forEach(r -> roles.add(r.toString().toUpperCase()));
        }
        List<String> directos = jwt.getClaimAsStringList("roles");
        if (directos != null) directos.forEach(r -> roles.add(r.toUpperCase()));
        return roles;
    }

    public boolean esAdmin(Jwt jwt) {
        return roles(jwt).contains("ADMIN");
    }

    /** docenteId de planificación; null si el usuario es ADMIN (acceso sin restricción de dueño). */
    public Long docenteId(Jwt jwt) {
        if (esAdmin(jwt)) return null;
        PlanificacionClient.DocenteInterno d;
        try {
            d = planificacionClient.getDocentePorUsuario(usuarioId(jwt));
        } catch (feign.FeignException.NotFound e) {
            d = null;
        }
        if (d == null || d.docenteId() == null) {
            throw new AccessDeniedException("El usuario no está registrado como docente.");
        }
        return d.docenteId();
    }

    public Long alumnoId(Jwt jwt) {
        AlumnosClient.AlumnoUsuario a = alumnoDeUsuarioOpcional(usuarioId(jwt));
        if (a == null || a.alumnoId() == null) {
            throw new AccessDeniedException("El usuario no está vinculado a un alumno.");
        }
        return a.alumnoId();
    }

    /** Alumnos cuyos datos puede ver el usuario: él mismo (ALUMNO) o sus hijos (APODERADO). */
    public Set<Long> alumnosVisibles(Jwt jwt) {
        Set<Long> ids = new HashSet<>();
        Set<String> roles = roles(jwt);
        Long uid = usuarioId(jwt);
        if (roles.contains("ALUMNO")) {
            AlumnosClient.AlumnoUsuario a = alumnoDeUsuarioOpcional(uid);
            if (a != null && a.alumnoId() != null) ids.add(a.alumnoId());
        }
        if (roles.contains("APODERADO")) {
            try {
                for (AlumnosClient.Hijo h : alumnosClient.hijosDeUsuario(uid)) ids.add(h.alumnoId());
            } catch (feign.FeignException.NotFound e) {
                // el usuario no tiene hijos registrados
            }
        }
        return ids;
    }

    /** alumnos-ms responde 404 si el usuario no es un alumno: se trata como "sin alumno", no como error. */
    private AlumnosClient.AlumnoUsuario alumnoDeUsuarioOpcional(Long usuarioId) {
        try {
            return alumnosClient.alumnoDeUsuario(usuarioId);
        } catch (feign.FeignException.NotFound e) {
            return null;
        }
    }
}
