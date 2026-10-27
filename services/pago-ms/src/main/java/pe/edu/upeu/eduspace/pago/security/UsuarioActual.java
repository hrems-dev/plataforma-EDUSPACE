package pe.edu.upeu.eduspace.pago.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;

public final class UsuarioActual {

    private UsuarioActual() {}

    /** usuarioId del JWT de auth-ms; sin él no hay identidad (no se asume ningún valor por defecto). */
    public static Long id(Jwt jwt) {
        Object v = jwt == null ? null : jwt.getClaim("usuarioId");
        if (v instanceof Number n) return n.longValue();
        if (v instanceof String s && s.matches("\\d+")) return Long.parseLong(s);
        throw new AccessDeniedException("El token no contiene el claim usuarioId.");
    }
}
