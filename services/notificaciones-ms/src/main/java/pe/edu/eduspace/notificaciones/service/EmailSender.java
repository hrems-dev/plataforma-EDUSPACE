package pe.edu.eduspace.notificaciones.service;

/** Envía un correo; lanza una excepción si el servidor SMTP lo rechaza (el reintento la registra). */
public interface EmailSender {
    void enviar(String para, String asunto, String cuerpo);
}
