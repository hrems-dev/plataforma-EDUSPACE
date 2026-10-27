package pe.edu.eduspace.alumnos.util;

import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/** Datos del usuario autenticado: claim {@code usuarioId} del token y sus roles. */
public final class UsuarioActual {

    private UsuarioActual() {
    }

    public static Long id() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            Object claim = jwt.getClaim("usuarioId");
            if (claim instanceof Number n) {
                return n.longValue();
            }
            if (claim instanceof String s && !s.isBlank()) {
                return Long.valueOf(s);
            }
        }
        return null;
    }

    /** Roles sin el prefijo {@code ROLE_}. */
    public static Set<String> roles() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return Set.of();
        }
        return auth.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_")).map(a -> a.substring(5)).collect(Collectors.toSet());
    }

    public static boolean tieneAlgunRol(String... roles) {
        Set<String> mios = roles();
        for (String rol : roles) {
            if (mios.contains(rol)) {
                return true;
            }
        }
        return false;
    }
}
