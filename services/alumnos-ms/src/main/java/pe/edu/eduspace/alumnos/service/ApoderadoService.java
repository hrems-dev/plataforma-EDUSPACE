package pe.edu.eduspace.alumnos.service;

import java.util.List;
import org.springframework.data.domain.Pageable;
import pe.edu.eduspace.alumnos.dto.*;
import pe.edu.eduspace.alumnos.entity.Apoderado;

public interface ApoderadoService {
    PageResponse<ApoderadoResponse> buscar(String q, Pageable pageable);

    ApoderadoResponse obtener(Long id);

    ApoderadoResponse actualizar(Long id, ApoderadoRequest request);

    List<HijoResponse> hijos(Long id);

    /** Reutiliza el apoderado con ese documento (completando sus datos de contacto) o crea uno nuevo. */
    Apoderado crearOReutilizar(ApoderadoRequest request);

    /** Apoderado existente por id o, si se envían sus datos, el reutilizado/creado por documento (exactamente uno de los dos). */
    Apoderado resolver(Long apoderadoId, ApoderadoRequest datos);

    /** Vincula la cuenta de auth-ms al apoderado. */
    ApoderadoResponse vincularUsuario(Long id, Long usuarioId);
}
