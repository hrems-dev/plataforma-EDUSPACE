package pe.edu.eduspace.notificaciones.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import pe.edu.eduspace.notificaciones.dto.DispositivoResponse;
import pe.edu.eduspace.notificaciones.dto.PlantillaResponse;
import pe.edu.eduspace.notificaciones.dto.PreferenciaResponse;
import pe.edu.eduspace.notificaciones.entity.DispositivoPush;
import pe.edu.eduspace.notificaciones.entity.PlantillaNotificacion;
import pe.edu.eduspace.notificaciones.entity.PreferenciaNotificacion;

/** Conversiones entidad → DTO. La bandeja arma su respuesta aparte porque parsea el JSON de datos. */
@Mapper
public interface NotificacionMapper {

    PlantillaResponse toResponse(PlantillaNotificacion plantilla);

    PreferenciaResponse toResponse(PreferenciaNotificacion preferencia);

    DispositivoResponse toResponse(DispositivoPush dispositivo);
}
