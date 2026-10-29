package pe.edu.eduspace.notificaciones.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import pe.edu.eduspace.notificaciones.dto.PlantillaCrearRequest;
import pe.edu.eduspace.notificaciones.dto.PlantillaRequest;
import pe.edu.eduspace.notificaciones.dto.PlantillaResponse;
import pe.edu.eduspace.notificaciones.entity.Canal;
import pe.edu.eduspace.notificaciones.entity.PlantillaNotificacion;

public interface PlantillaService {

    /** Texto ya armado de una plantilla: asunto (título) y cuerpo (mensaje). */
    record Texto(String asunto, String cuerpo) {
    }

    Optional<PlantillaNotificacion> activa(String codigo, Canal canal);

    /** Reemplaza los marcadores {{campo}}; un dato faltante deja un texto neutro en lugar de fallar. */
    Texto armar(PlantillaNotificacion plantilla, Map<String, String> datos);

    List<PlantillaResponse> listar();

    PlantillaResponse crear(PlantillaCrearRequest request);

    PlantillaResponse actualizar(Long id, PlantillaRequest request);

    /** Solo las plantillas propias: las que usa el sistema para sus avisos automáticos no se borran (se desactivan). */
    void eliminar(Long id);
}
