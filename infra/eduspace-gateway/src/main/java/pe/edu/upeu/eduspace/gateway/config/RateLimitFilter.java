package pe.edu.upeu.eduspace.gateway.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Límite de peticiones por IP (cubo de fichas en memoria): un cubo general para toda la API y otro más estricto para
 * login/refresh, que frena la fuerza bruta antes de llegar a auth-ms. Con varias réplicas del Gateway el límite es por réplica.
 * Detrás de un balanceador, activar {@code server.forward-headers-strategy=framework} para que la IP sea la del cliente.
 */
@Component
@EnableScheduling
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final long NANOS_POR_MINUTO = 60_000_000_000L;

    private final boolean activo;
    private final Limite general;
    private final Limite login;
    private final Map<String, Cubo> cubos = new ConcurrentHashMap<>();

    public RateLimitFilter(
            @Value("${eduspace.gateway.rate-limit.enabled:true}") boolean activo,
            @Value("${eduspace.gateway.rate-limit.requests-per-minute:300}") int porMinuto,
            @Value("${eduspace.gateway.rate-limit.burst:50}") int rafaga,
            @Value("${eduspace.gateway.rate-limit.login-per-minute:10}") int loginPorMinuto,
            @Value("${eduspace.gateway.rate-limit.login-burst:5}") int loginRafaga) {
        this.activo = activo;
        this.general = new Limite(porMinuto, rafaga);
        this.login = new Limite(loginPorMinuto, loginRafaga);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !activo || request.getRequestURI().startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean esLogin = "POST".equals(request.getMethod())
                && (request.getRequestURI().equals("/api/v1/auth/login") || request.getRequestURI().equals("/api/v1/auth/refresh"));
        Limite limite = esLogin ? login : general;
        String clave = (esLogin ? "login:" : "api:") + request.getRemoteAddr();
        long segundosEspera = cubos.computeIfAbsent(clave, k -> new Cubo(limite)).tomar(limite);
        if (segundosEspera > 0) {
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(segundosEspera));
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Demasiadas peticiones. Intente de nuevo en unos segundos.\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    /** Descarta los cubos que ya se llenaron del todo: evita que el mapa crezca con IPs que no vuelven. */
    @Scheduled(fixedDelay = 300_000)
    void limpiar() {
        long ahora = System.nanoTime();
        cubos.entrySet().removeIf(e -> e.getValue().ocioso(ahora));
    }

    record Limite(int porMinuto, int rafaga) { }

    static final class Cubo {
        private double fichas;
        private long ultimo = System.nanoTime();

        Cubo(Limite l) {
            this.fichas = l.rafaga();
        }

        /** Devuelve 0 si hay ficha; si no, los segundos hasta que haya una. */
        synchronized long tomar(Limite l) {
            long ahora = System.nanoTime();
            fichas = Math.min(l.rafaga(), fichas + (ahora - ultimo) * (double) l.porMinuto() / NANOS_POR_MINUTO);
            ultimo = ahora;
            if (fichas >= 1) {
                fichas -= 1;
                return 0;
            }
            return Math.max(1, (long) Math.ceil((1 - fichas) * 60.0 / l.porMinuto()));
        }

        synchronized boolean ocioso(long ahora) {
            return ahora - ultimo > 2 * NANOS_POR_MINUTO;
        }
    }
}
