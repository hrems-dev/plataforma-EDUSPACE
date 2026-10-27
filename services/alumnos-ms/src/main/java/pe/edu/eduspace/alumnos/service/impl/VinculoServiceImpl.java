package pe.edu.eduspace.alumnos.service.impl;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.eduspace.alumnos.dto.VinculoApoderadoRequest;
import pe.edu.eduspace.alumnos.dto.VinculoResponse;
import pe.edu.eduspace.alumnos.dto.VinculoUpdateRequest;
import pe.edu.eduspace.alumnos.entity.Alumno;
import pe.edu.eduspace.alumnos.entity.Apoderado;
import pe.edu.eduspace.alumnos.entity.AlumnoApoderado;
import pe.edu.eduspace.alumnos.exception.BusinessRuleException;
import pe.edu.eduspace.alumnos.exception.DuplicateResourceException;
import pe.edu.eduspace.alumnos.exception.ResourceNotFoundException;
import pe.edu.eduspace.alumnos.mapper.AlumnoMapper;
import pe.edu.eduspace.alumnos.repository.AlumnoApoderadoRepository;
import pe.edu.eduspace.alumnos.repository.AlumnoRepository;
import pe.edu.eduspace.alumnos.service.ApoderadoService;
import pe.edu.eduspace.alumnos.service.VinculoService;

/** Las banderas "principal" y "responsable de pago" son únicas por alumno: se quitan de los demás antes de asignarlas. */
@Service
@RequiredArgsConstructor
@Transactional
public class VinculoServiceImpl implements VinculoService {

    private static final long NINGUNO = -1L;

    private final AlumnoApoderadoRepository repository;
    private final AlumnoRepository alumnoRepository;
    private final ApoderadoService apoderadoService;
    private final AlumnoMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public List<VinculoResponse> listar(Long alumnoId) {
        existeAlumno(alumnoId);
        return repository.findByAlumno(alumnoId).stream().map(mapper::toResponse).toList();
    }

    @Override
    public VinculoResponse agregar(Long alumnoId, VinculoApoderadoRequest request) {
        existeAlumno(alumnoId);
        boolean principal = Boolean.TRUE.equals(request.esPrincipal());
        boolean pagador = Boolean.TRUE.equals(request.esResponsablePago());
        if (repository.countByAlumnoId(alumnoId) == 0) {
            principal = true;
        }
        // primero se liberan las banderas únicas (las consultas masivas limpian el contexto de persistencia)
        if (principal) {
            repository.quitarPrincipalDeLosDemas(alumnoId, NINGUNO);
        }
        if (pagador) {
            repository.quitarResponsablePagoDeLosDemas(alumnoId, NINGUNO);
        }
        Alumno alumno = alumnoRepository.findById(alumnoId).orElseThrow();
        Apoderado apoderado = apoderadoService.resolver(request.apoderadoId(), request.apoderado());
        if (repository.existsByAlumnoIdAndApoderadoId(alumnoId, apoderado.getId())) {
            throw new DuplicateResourceException("El apoderado ya está vinculado a este alumno");
        }
        AlumnoApoderado vinculo = new AlumnoApoderado();
        vinculo.setAlumno(alumno);
        vinculo.setApoderado(apoderado);
        vinculo.setParentesco(request.parentesco());
        vinculo.setEsPrincipal(principal);
        vinculo.setEsResponsablePago(pagador);
        vinculo.setViveConAlumno(request.viveConAlumno() == null || request.viveConAlumno());
        vinculo.setPuedeRecoger(request.puedeRecoger() == null || request.puedeRecoger());
        return mapper.toResponse(repository.save(vinculo));
    }

    @Override
    public VinculoResponse actualizar(Long alumnoId, Long vinculoId, VinculoUpdateRequest request) {
        AlumnoApoderado actual = buscar(alumnoId, vinculoId);
        if (Boolean.FALSE.equals(request.esPrincipal()) && Boolean.TRUE.equals(actual.getEsPrincipal())) {
            throw new BusinessRuleException("El alumno no puede quedar sin apoderado principal: designe primero a otro como principal");
        }
        if (Boolean.TRUE.equals(request.esPrincipal())) {
            repository.quitarPrincipalDeLosDemas(alumnoId, vinculoId);
        }
        if (Boolean.TRUE.equals(request.esResponsablePago())) {
            repository.quitarResponsablePagoDeLosDemas(alumnoId, vinculoId);
        }
        AlumnoApoderado vinculo = buscar(alumnoId, vinculoId);
        if (request.parentesco() != null) {
            vinculo.setParentesco(request.parentesco());
        }
        if (request.esPrincipal() != null) {
            vinculo.setEsPrincipal(request.esPrincipal());
        }
        if (request.esResponsablePago() != null) {
            vinculo.setEsResponsablePago(request.esResponsablePago());
        }
        if (request.viveConAlumno() != null) {
            vinculo.setViveConAlumno(request.viveConAlumno());
        }
        if (request.puedeRecoger() != null) {
            vinculo.setPuedeRecoger(request.puedeRecoger());
        }
        return mapper.toResponse(repository.save(vinculo));
    }

    @Override
    public void quitar(Long alumnoId, Long vinculoId) {
        AlumnoApoderado vinculo = buscar(alumnoId, vinculoId);
        if (Boolean.TRUE.equals(vinculo.getEsPrincipal())) {
            throw new BusinessRuleException("No se puede quitar al apoderado principal: designe primero a otro como principal");
        }
        repository.delete(vinculo);
    }

    private AlumnoApoderado buscar(Long alumnoId, Long vinculoId) {
        return repository.findByIdAndAlumnoId(vinculoId, alumnoId)
                .orElseThrow(() -> ResourceNotFoundException.of("Vínculo", vinculoId));
    }

    private void existeAlumno(Long alumnoId) {
        if (!alumnoRepository.existsById(alumnoId)) {
            throw ResourceNotFoundException.of("Alumno", alumnoId);
        }
    }
}
