package pe.edu.upeu.eduspace.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import pe.edu.upeu.eduspace.gateway.config.RateLimitFilter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RateLimitFilterTest {

    private int llamar(RateLimitFilter filtro, String metodo, String uri, String ip) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest(metodo, uri);
        req.setRemoteAddr(ip);
        MockHttpServletResponse res = new MockHttpServletResponse();
        filtro.doFilter(req, res, new MockFilterChain());
        if (res.getStatus() == 429) {
            assertNotNull(res.getHeader("Retry-After"));
        }
        return res.getStatus();
    }

    @Test
    void loginSeLimitaPorIpYNoAfectaAOtrasIps() throws Exception {
        RateLimitFilter filtro = new RateLimitFilter(true, 300, 50, 10, 3);
        for (int i = 0; i < 3; i++) {
            assertEquals(200, llamar(filtro, "POST", "/api/v1/auth/login", "10.0.0.1"));
        }
        assertEquals(429, llamar(filtro, "POST", "/api/v1/auth/login", "10.0.0.1"));
        assertEquals(200, llamar(filtro, "POST", "/api/v1/auth/login", "10.0.0.2"));
    }

    @Test
    void elCuboDeLoginNoConsumeElDeLaApi() throws Exception {
        RateLimitFilter filtro = new RateLimitFilter(true, 300, 5, 10, 1);
        assertEquals(200, llamar(filtro, "POST", "/api/v1/auth/login", "10.0.0.3"));
        assertEquals(429, llamar(filtro, "POST", "/api/v1/auth/login", "10.0.0.3"));
        assertEquals(200, llamar(filtro, "GET", "/api/v1/alumnos", "10.0.0.3"));
    }

    @Test
    void laApiGeneralCortaAlSuperarLaRafaga() throws Exception {
        RateLimitFilter filtro = new RateLimitFilter(true, 60, 4, 10, 5);
        for (int i = 0; i < 4; i++) {
            assertEquals(200, llamar(filtro, "GET", "/api/v1/pagos/mias", "10.0.0.4"));
        }
        assertEquals(429, llamar(filtro, "GET", "/api/v1/pagos/mias", "10.0.0.4"));
    }

    @Test
    void actuatorNoSeLimitaYDesactivadoNoLimitaNada() throws Exception {
        RateLimitFilter filtro = new RateLimitFilter(true, 60, 1, 10, 1);
        for (int i = 0; i < 5; i++) {
            assertEquals(200, llamar(filtro, "GET", "/actuator/health", "10.0.0.5"));
        }
        RateLimitFilter apagado = new RateLimitFilter(false, 60, 1, 10, 1);
        for (int i = 0; i < 5; i++) {
            assertEquals(200, llamar(apagado, "GET", "/api/v1/alumnos", "10.0.0.6"));
        }
    }
}
