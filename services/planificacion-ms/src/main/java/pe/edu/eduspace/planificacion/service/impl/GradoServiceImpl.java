package pe.edu.eduspace.planificacion.service.impl;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.eduspace.planificacion.dto.CatalogoUpdateRequest;
import pe.edu.eduspace.planificacion.dto.GradoResponse;
import pe.edu.eduspace.planificacion.entity.Grado;
import pe.edu.eduspace.planificacion.exception.ResourceNotFoundException;
import pe.edu.eduspace.planificacion.mapper.PlanificacionMapper;
import pe.edu.eduspace.planificacion.repository.GradoRepository;
import pe.edu.eduspace.planificacion.service.GradoService;

@Service
@RequiredArgsConstructor
@Transactional
public class GradoServiceImpl implements GradoService {

    private final GradoRepository repository;
    private final PlanificacionMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public List<GradoResponse> listar(Long nivelId) {
        List<Grado> grados = nivelId == null
                ? repository.findAllByOrderByNivelOrdenAscNumeroAsc()
                : repository.findByNivelIdOrderByNumero(nivelId);
        return grados.stream().map(mapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public GradoResponse obtener(Long id) {
        return mapper.toResponse(buscar(id));
    }

    @Override
    public GradoResponse actualizar(Long id, CatalogoUpdateRequest request) {
        Grado grado = buscar(id);
        grado.setNombre(request.nombre());
        if (request.activo() != null) {
            grado.setActivo(request.activo());
        }
        return mapper.toResponse(repository.save(grado));
    }

    private Grado buscar(Long id) {
        return repository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Grado", id));
    }
}
