package pe.edu.eduspace.planificacion.util;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/** Lee el claim {@code usuarioId} del token del usuario autenticado. */
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
}
