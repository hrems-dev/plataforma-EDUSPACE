package pe.edu.eduspace.notificaciones.dto;

import pe.edu.eduspace.notificaciones.entity.Canal;

public record PreferenciaResponse(String tipo, Canal canal, Boolean habilitada) {
}
