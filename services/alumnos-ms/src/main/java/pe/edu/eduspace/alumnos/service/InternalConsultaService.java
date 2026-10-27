package pe.edu.eduspace.alumnos.service;

import java.util.List;
import pe.edu.eduspace.alumnos.dto.*;

/** Lecturas livianas para otros servicios (adenda A3). */
public interface InternalConsultaService {
    AlumnoBasicoResponse basico(Long alumnoId);

    AlumnoUsuarioResponse alumnoDeUsuario(Long usuarioId);

    List<HijoResponse> hijosDeUsuario(Long usuarioId);

    List<AlumnoBasicoResponse> lote(List<Long> ids);

    ResponsablePagoResponse responsablePago(Long alumnoId);
}
