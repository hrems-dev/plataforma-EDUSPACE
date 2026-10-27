package pe.edu.upeu.eduspace.matricula.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upeu.eduspace.matricula.dto.Dtos.CampaniaRequest;
import pe.edu.upeu.eduspace.matricula.dto.Dtos.CampaniaResponse;
import pe.edu.upeu.eduspace.matricula.entity.CampaniaMatricula;
import pe.edu.upeu.eduspace.matricula.exception.BusinessRuleException;
import pe.edu.upeu.eduspace.matricula.exception.ResourceNotFoundException;
import pe.edu.upeu.eduspace.matricula.repository.CampaniaRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CampaniaService {

    private final CampaniaRepository repo;

    @Transactional(readOnly = true)
    public List<CampaniaResponse> listar(Long anioId) {
        var lista = anioId != null ? repo.findByAnioIdOrderByFechaInicioDesc(anioId) : repo.findAllByOrderByFechaInicioDesc();
        return lista.stream().map(MatriculaMapper::toResponse).toList();
    }

    @Transactional
    public CampaniaResponse crear(CampaniaRequest req) {
        validar(req);
        if (repo.existsByAnioIdAndNombre(req.anioId(), req.nombre().trim())) {
            throw new BusinessRuleException("Ya existe una campaña con ese nombre para el año.");
        }
        return MatriculaMapper.toResponse(repo.save(CampaniaMatricula.builder()
            .anioId(req.anioId()).nombre(req.nombre().trim())
            .fechaInicio(req.fechaInicio()).fechaFin(req.fechaFin()).estado("PLANIFICADA").build()));
    }

    @Transactional
    public CampaniaResponse actualizar(Long id, CampaniaRequest req) {
        validar(req);
        CampaniaMatricula c = obtener(id);
        if ("CERRADA".equals(c.getEstado())) {
            throw new BusinessRuleException("Una campaña CERRADA no se puede modificar.");
        }
        c.setNombre(req.nombre().trim());
        c.setFechaInicio(req.fechaInicio());
        c.setFechaFin(req.fechaFin());
        return MatriculaMapper.toResponse(repo.save(c));
    }

    /** Solo una campaña ABIERTA por año. */
    @Transactional
    public CampaniaResponse abrir(Long id) {
        CampaniaMatricula c = obtener(id);
        if ("ABIERTA".equals(c.getEstado())) return MatriculaMapper.toResponse(c);
        if ("CERRADA".equals(c.getEstado())) {
            throw new BusinessRuleException("Una campaña CERRADA no se puede reabrir; cree una nueva.");
        }
        repo.findFirstByAnioIdAndEstado(c.getAnioId(), "ABIERTA").ifPresent(otra -> {
            throw new BusinessRuleException("Ya hay una campaña ABIERTA para este año: " + otra.getNombre());
        });
        c.setEstado("ABIERTA");
        return MatriculaMapper.toResponse(repo.save(c));
    }

    @Transactional
    public CampaniaResponse cerrar(Long id) {
        CampaniaMatricula c = obtener(id);
        if (!"ABIERTA".equals(c.getEstado())) {
            throw new BusinessRuleException("Solo se puede cerrar una campaña ABIERTA.");
        }
        c.setEstado("CERRADA");
        return MatriculaMapper.toResponse(repo.save(c));
    }

    private CampaniaMatricula obtener(Long id) {
        return repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Campaña no encontrada: " + id));
    }

    private void validar(CampaniaRequest req) {
        if (req.fechaFin().isBefore(req.fechaInicio())) {
            throw new BusinessRuleException("La fecha fin no puede ser anterior a la fecha inicio.");
        }
    }
}
