package pe.edu.eduspace.notificaciones.integration;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Simula alumnos-ms: {@code GET /internal/alumnos/{id}/contactos}. Se puede "apagar" (503) y contar las llamadas. */
final class AlumnosStub {

    private final HttpServer server;
    private final Map<Long, String> contactos = new ConcurrentHashMap<>();
    private final AtomicInteger llamadas = new AtomicInteger();
    private volatile boolean caido;

    AlumnosStub() {
        try {
            server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        server.createContext("/internal/alumnos/", this::atender);
        server.start();
    }

    String url() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    int llamadas() {
        return llamadas.get();
    }

    void caido(boolean caido) {
        this.caido = caido;
    }

    void reiniciar() {
        contactos.clear();
        llamadas.set(0);
        caido = false;
    }

    /** Alumno con su cuenta y apoderados dados como JSON ya armado. */
    void alumno(long id, String nombre, Long usuarioId, String... apoderadosJson) {
        String json = "{\"alumnoId\":" + id + ",\"nombreCompleto\":\"" + nombre + "\",\"usuarioId\":" + usuarioId
                + ",\"apoderados\":[" + String.join(",", apoderadosJson) + "],\"campoQueNoUsamos\":true}";
        contactos.put(id, json);
    }

    static String apoderado(long id, Long usuarioId, String nombre, String email, boolean principal, boolean pago) {
        return "{\"apoderadoId\":" + id + ",\"usuarioId\":" + usuarioId + ",\"nombreCompleto\":\"" + nombre + "\",\"email\":"
                + (email == null ? "null" : "\"" + email + "\"") + ",\"telefono\":\"999\",\"esPrincipal\":" + principal
                + ",\"esResponsablePago\":" + pago + ",\"parentesco\":\"PADRE\"}";
    }

    private void atender(HttpExchange ex) throws IOException {
        llamadas.incrementAndGet();
        String[] partes = ex.getRequestURI().getPath().split("/");   // ["", internal, alumnos, {id}, contactos]
        int status = 200;
        String cuerpo;
        if (caido) {
            status = 503;
            cuerpo = "{\"error\":\"caido\"}";
        } else {
            String json = partes.length >= 5 ? contactos.get(Long.parseLong(partes[3])) : null;
            if (json == null) {
                status = 404;
                cuerpo = "{\"codigo\":\"NO_ENCONTRADO\"}";
            } else {
                cuerpo = json;
            }
        }
        byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(status, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
    }
}
