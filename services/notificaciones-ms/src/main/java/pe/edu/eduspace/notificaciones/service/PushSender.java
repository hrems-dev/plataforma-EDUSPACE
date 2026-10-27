package pe.edu.eduspace.notificaciones.service;

import java.util.List;

/** Envío a dispositivos. MVP: {@code LogPushSender}; fase 2: implementación con FCM. */
public interface PushSender {
    void enviar(List<String> tokens, String titulo, String mensaje);
}
