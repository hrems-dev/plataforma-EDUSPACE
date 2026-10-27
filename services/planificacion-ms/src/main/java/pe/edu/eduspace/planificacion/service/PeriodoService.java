package pe.edu.eduspace.planificacion.service;

import java.util.List;
import pe.edu.eduspace.planificacion.dto.PeriodoRequest;
import pe.edu.eduspace.planificacion.dto.PeriodoResponse;
import pe.edu.eduspace.planificacion.entity.EstadoPeriodo;

public interface PeriodoService {
    List<PeriodoResponse> listar(Long anioId);

    PeriodoResponse obtener(Long id);

    PeriodoResponse crear(PeriodoRequest request);

    PeriodoResponse actualizar(Long id, PeriodoRequest request);

    PeriodoResponse cambiarEstado(Long id, EstadoPeriodo estado);

    void eliminar(Long id);
}
