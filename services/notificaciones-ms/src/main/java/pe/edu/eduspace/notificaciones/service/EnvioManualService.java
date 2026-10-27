package pe.edu.eduspace.notificaciones.service;

import pe.edu.eduspace.notificaciones.dto.EnvioManualRequest;
import pe.edu.eduspace.notificaciones.dto.EnvioManualResponse;

public interface EnvioManualService {
    EnvioManualResponse enviar(EnvioManualRequest request);
}
