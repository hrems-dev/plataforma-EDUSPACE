package pe.edu.upeu.eduspace.auth.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** {@code /internal/**} solo responde a otro servicio: exige el header {@code X-Service-Token} con el token compartido. */
@Component
public class ServiceTokenFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Service-Token";

    private final byte[] esperado;

    public ServiceTokenFilter(@Value("${eduspace.service-token}") String token) {
        this.esperado = token.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String recibido = request.getHeader(HEADER);
        boolean valido = esperado.length > 0 && recibido != null
                && MessageDigest.isEqual(esperado, recibido.getBytes(StandardCharsets.UTF_8));
        if (!valido) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        chain.doFilter(request, response);
    }
}
