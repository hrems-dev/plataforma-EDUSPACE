package pe.edu.eduspace.planificacion.service;

import java.time.LocalDate;
import java.util.List;
import pe.edu.eduspace.planificacion.dto.EsHabilResponse;
import pe.edu.eduspace.planificacion.dto.FeriadoRequest;
import pe.edu.eduspace.planificacion.dto.FeriadoResponse;

public interface FeriadoService {
    List<FeriadoResponse> listar(Long anioId);

    FeriadoResponse crear(FeriadoRequest request);

    FeriadoResponse actualizar(Long id, FeriadoRequest request);

    void eliminar(Long id);

    EsHabilResponse esHabil(LocalDate fecha);
}
