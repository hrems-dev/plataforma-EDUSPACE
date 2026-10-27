package pe.edu.eduspace.planificacion.service.impl;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.eduspace.planificacion.dto.SeccionRequest;
import pe.edu.eduspace.planificacion.dto.SeccionResponse;
import pe.edu.eduspace.planificacion.entity.*;
import pe.edu.eduspace.planificacion.exception.BusinessRuleException;
import pe.edu.eduspace.planificacion.exception.ResourceNotFoundException;
import pe.edu.eduspace.planificacion.mapper.PlanificacionMapper;
import pe.edu.eduspace.planificacion.repository.*;
import pe.edu.eduspace.planificacion.service.SeccionService;

@Service
@RequiredArgsConstructor
@Transactional
public class SeccionServiceImpl implements SeccionService {

    private final SeccionRepository repository;
    private final AnioAcademicoRepository anioRepository;
    private final GradoRepository gradoRepository;
    private final AulaRepository aulaRepository;
    private final DocenteRepository docenteRepository;
    private final AsignacionRepository asignacionRepository;
    private final HorarioRepository horarioRepository;
    private final PlanificacionMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public List<SeccionResponse> listar(Long anioId, Long gradoId) {
        return repository.buscar(anioId, gradoId).stream().map(mapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public SeccionResponse obtener(Long id) {
        return mapper.toResponse(buscar(id));
    }

    @Override
    public SeccionResponse crear(SeccionRequest request) {
        Seccion seccion = new Seccion();
        aplicar(seccion, request, null);
        return mapper.toResponse(repository.save(seccion));
    }

    @Override
    public SeccionResponse actualizar(Long id, SeccionRequest request) {
        Seccion seccion = buscar(id);
        aplicar(seccion, request, id);
        return mapper.toResponse(repository.save(seccion));
    }

    @Override
    public void eliminar(Long id) {
        Seccion seccion = buscar(id);
        if (seccion.getAnio().getEstado() == EstadoPeriodo.CERRADO) {
            throw new BusinessRuleException("El año de la sección está CERRADO (solo lectura)");
        }
        if (!asignacionRepository.findBySeccionId(id).isEmpty()) {
            throw new BusinessRuleException("La sección tiene cursos asignados: elimine primero las asignaciones");
        }
        repository.delete(seccion);
    }

    private void aplicar(Seccion seccion, SeccionRequest r, Long id) {
        AnioAcademico anio = anioRepository.findById(r.anioId())
                .orElseThrow(() -> ResourceNotFoundException.of("Año académico", r.anioId()));
        if (anio.getEstado() == EstadoPeriodo.CERRADO) {
            throw new BusinessRuleException("El año " + anio.getNombre() + " está CERRADO (solo lectura)");
        }
        Grado grado = gradoRepository.findById(r.gradoId())
                .orElseThrow(() -> ResourceNotFoundException.of("Grado", r.gradoId()));
        boolean repetida = id == null
                ? repository.existsByAnioIdAndGradoIdAndNombre(r.anioId(), r.gradoId(), r.nombre())
                : repository.existsByAnioIdAndGradoIdAndNombreAndIdNot(r.anioId(), r.gradoId(), r.nombre(), id);
        if (repetida) {
            throw new BusinessRuleException("Ya existe la sección " + r.nombre() + " de " + grado.getNombre() + " en el año " + anio.getNombre());
        }
        Aula aula = null;
        if (r.aulaId() != null) {
            aula = aulaRepository.findById(r.aulaId()).orElseThrow(() -> ResourceNotFoundException.of("Aula", r.aulaId()));
            if (!Boolean.TRUE.equals(aula.getActivo())) {
                throw new BusinessRuleException("El aula " + aula.getCodigo() + " está inactiva");
            }
        }
        short capacidad = r.capacidad() == null ? 30 : r.capacidad();
        if (aula != null && capacidad > aula.getCapacidad()) {
            throw new BusinessRuleException("La capacidad de la sección (" + capacidad
                    + ") supera la del aula " + aula.getCodigo() + " (" + aula.getCapacidad() + ")");
        }
        Docente tutor = null;
        if (r.tutorDocenteId() != null) {
            tutor = docenteRepository.findById(r.tutorDocenteId())
                    .orElseThrow(() -> ResourceNotFoundException.of("Docente", r.tutorDocenteId()));
            if (tutor.getEstado() != EstadoDocente.ACTIVO) {
                throw new BusinessRuleException("El tutor debe ser un docente ACTIVO");
            }
        }
        Turno turno = r.turno() == null ? Turno.MANANA : r.turno();
        if (id != null && seccion.getTurno() != turno && horarioHaySobre(id)) {
            throw new BusinessRuleException("No se puede cambiar el turno de una sección que ya tiene clases en el horario");
        }
        seccion.setAnio(anio);
        seccion.setGrado(grado);
        seccion.setAula(aula);
        seccion.setNombre(r.nombre());
        seccion.setTurno(turno);
        seccion.setCapacidad(capacidad);
        seccion.setTutor(tutor);
        seccion.setActivo(r.activo() == null || r.activo());
    }

    private boolean horarioHaySobre(Long seccionId) {
        return !horarioRepository.findBySeccion(seccionId).isEmpty();
    }

    private Seccion buscar(Long id) {
        return repository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Sección", id));
    }
}
