package pe.edu.eduspace.alumnos.dto;

import java.util.List;

/** Contrato interno con notificaciones-ms. */
public record ContactosNotificacionResponse(Long alumnoId, String nombreCompleto, Long usuarioId,
                                            List<ApoderadoContactoResponse> apoderados) {
}
