package pe.edu.upeu.eduspace.matricula.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/** Envío de correos (notificaciones-ms, solo con el token de servicio). */
@FeignClient(name = "notificaciones-ms")
public interface NotificacionesClient {

    @PostMapping("/internal/correos")
    Respuesta enviar(@RequestBody Correo body);

    record Correo(String para, String asunto, String html, String texto, List<Adjunto> adjuntos) {}

    record Adjunto(String nombre, String contentType, String base64) {}

    record Respuesta(boolean enviado) {}
}
