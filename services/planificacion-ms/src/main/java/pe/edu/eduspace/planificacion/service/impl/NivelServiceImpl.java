package pe.edu.eduspace.planificacion.service.impl;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.eduspace.planificacion.dto.CatalogoUpdateRequest;
import pe.edu.eduspace.planificacion.dto.NivelResponse;
import pe.edu.eduspace.planificacion.entity.Nivel;
import pe.edu.eduspace.planificacion.exception.ResourceNotFoundException;
import pe.edu.eduspace.planificacion.mapper.PlanificacionMapper;
import pe.edu.eduspace.planificacion.repository.NivelRepository;
import pe.edu.eduspace.planificacion.service.NivelService;

@Service
@RequiredArgsConstructor
@Transactional
public class NivelServiceImpl implements NivelService {

    private final NivelRepository repository;
    private final PlanificacionMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public List<NivelResponse> listar() {
        return repository.findAllByOrderByOrden().stream().map(mapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public NivelResponse obtener(Long id) {
        return mapper.toResponse(buscar(id));
    }

    @Override
    public NivelResponse actualizar(Long id, CatalogoUpdateRequest request) {
        Nivel nivel = buscar(id);
        nivel.setNombre(request.nombre());
        if (request.activo() != null) {
            nivel.setActivo(request.activo());
        }
        return mapper.toResponse(repository.save(nivel));
    }

    private Nivel buscar(Long id) {
        return repository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Nivel", id));
    }
}
