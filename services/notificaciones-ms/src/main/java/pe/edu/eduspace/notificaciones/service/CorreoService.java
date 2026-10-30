package pe.edu.eduspace.notificaciones.service;

import jakarta.mail.internet.MimeMessage;
import java.util.Base64;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import pe.edu.eduspace.notificaciones.config.AppProperties;
import pe.edu.eduspace.notificaciones.exception.BusinessRuleException;

/**
 * Envía un correo con cuerpo HTML y archivos adjuntos (p. ej. la constancia de matrícula en PDF). Lo piden otros servicios
 * con el token de servicio. En DEV el servidor SMTP es Mailpit; en PROD se configura {@code spring.mail.*} con el proveedor real.
 */
@Slf4j
@Service
public class CorreoService {

    public record Adjunto(String nombre, String contentType, String base64) {}

    private final JavaMailSender mail;
    private final AppProperties props;

    public CorreoService(JavaMailSender mail, AppProperties props) {
        this.mail = mail;
        this.props = props;
    }

    public void enviar(String para, String asunto, String html, String texto, List<Adjunto> adjuntos) {
        try {
            MimeMessage m = mail.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(m, true, "UTF-8");   // siempre multipart: texto + HTML (con o sin adjuntos)
            h.setFrom(props.mail().from());
            h.setTo(para);
            h.setSubject(asunto);
            h.setText(texto == null ? "" : texto, html);
            if (adjuntos != null) {
                for (Adjunto a : adjuntos) {
                    byte[] bytes = Base64.getDecoder().decode(a.base64());
                    h.addAttachment(a.nombre(), new ByteArrayResource(bytes), a.contentType() == null ? "application/octet-stream" : a.contentType());
                }
            }
            mail.send(m);
            log.info("Correo enviado a {} ({} adjunto(s))", enmascarar(para), adjuntos == null ? 0 : adjuntos.size());
        } catch (Exception e) {
            log.warn("No se pudo enviar el correo a {}: {}", enmascarar(para), e.getMessage());
            throw new BusinessRuleException("No se pudo enviar el correo: " + e.getMessage());
        }
    }

    /** En los registros no se escribe la dirección completa. */
    static String enmascarar(String correo) {
        int i = correo == null ? -1 : correo.indexOf('@');
        return i <= 1 ? "***" : correo.charAt(0) + "***" + correo.substring(i);
    }
}
