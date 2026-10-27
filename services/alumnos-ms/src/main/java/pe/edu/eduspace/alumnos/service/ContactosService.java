package pe.edu.eduspace.alumnos.service;

import pe.edu.eduspace.alumnos.dto.ContactosNotificacionResponse;

public interface ContactosService {
    ContactosNotificacionResponse contactos(Long alumnoId);
}
