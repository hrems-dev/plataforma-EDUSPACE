package pe.edu.eduspace.planificacion.service;

import java.util.List;
import pe.edu.eduspace.planificacion.dto.AnioRequest;
import pe.edu.eduspace.planificacion.dto.AnioResponse;

public interface AnioAcademicoService {
    List<AnioResponse> listar();

    AnioResponse obtener(Long id);

    AnioResponse activo();

    AnioResponse crear(AnioRequest request);

    AnioResponse actualizar(Long id, AnioRequest request);

    AnioResponse activar(Long id);

    AnioResponse cerrar(Long id);
}
