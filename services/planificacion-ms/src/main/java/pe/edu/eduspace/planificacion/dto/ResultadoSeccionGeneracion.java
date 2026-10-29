package pe.edu.eduspace.planificacion.dto;

import java.util.List;

/** {@code estado}: GENERADO, INCOMPLETO (quedaron horas sin lugar), SIN_CAMBIOS (ya estaba completo) u OMITIDO (no estaba lista). */
public record ResultadoSeccionGeneracion(Long seccionId, String nombre, String estado, int requeridas, int colocadas, List<String> mensajes) {
}
