package pe.edu.eduspace.notificaciones.dto;

import pe.edu.eduspace.notificaciones.entity.Plataforma;

public record DispositivoResponse(Long id, Plataforma plataforma, Boolean activo) {
}
