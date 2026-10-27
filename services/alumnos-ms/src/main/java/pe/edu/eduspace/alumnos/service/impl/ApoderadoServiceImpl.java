package pe.edu.eduspace.alumnos.service.impl;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.eduspace.alumnos.dto.*;
import pe.edu.eduspace.alumnos.entity.Apoderado;
import pe.edu.eduspace.alumnos.exception.DatosInvalidosException;
import pe.edu.eduspace.alumnos.exception.DuplicateResourceException;
import pe.edu.eduspace.alumnos.exception.ResourceNotFoundException;
import pe.edu.eduspace.alumnos.mapper.AlumnoMapper;
import pe.edu.eduspace.alumnos.repository.AlumnoApoderadoRepository;
import pe.edu.eduspace.alumnos.repository.ApoderadoRepository;
import pe.edu.eduspace.alumnos.service.ApoderadoService;
import pe.edu.eduspace.alumnos.util.DocumentoIdentidad;

@Service
@RequiredArgsConstructor
@Transactional
public class ApoderadoServiceImpl implements ApoderadoService {

    private final ApoderadoRepository repository;
    private final AlumnoApoderadoRepository vinculoRepository;
    private final AlumnoMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ApoderadoResponse> buscar(String q, Pageable pageable) {
        return PageResponse.of(repository.buscar(q == null ? "" : q.trim(), pageable).map(mapper::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public ApoderadoResponse obtener(Long id) {
        return mapper.toResponse(buscar(id));
    }

    @Override
    public ApoderadoResponse actualizar(Long id, ApoderadoRequest request) {
        Apoderado apoderado = buscar(id);
        String tipo = DocumentoIdentidad.validar(request.tipoDocumento(), request.numeroDocumento());
        if (repository.existsByTipoDocumentoAndNumeroDocumentoAndIdNot(tipo, request.numeroDocumento(), id)) {
            throw new DuplicateResourceException("Ya existe otro apoderado con " + tipo + " " + request.numeroDocumento());
        }
        mapper.update(request, apoderado);
        apoderado.setTipoDocumento(tipo);
        return mapper.toResponse(repository.save(apoderado));
    }

    @Override
    @Transactional(readOnly = true)
    public List<HijoResponse> hijos(Long id) {
        buscar(id);
        return vinculoRepository.findByApoderado(id).stream().map(mapper::toHijo).toList();
    }

    @Override
    public Apoderado crearOReutilizar(ApoderadoRequest request) {
        String tipo = DocumentoIdentidad.validar(request.tipoDocumento(), request.numeroDocumento());
        return repository.findByTipoDocumentoAndNumeroDocumento(tipo, request.numeroDocumento()).map(existente -> {
            // un apoderado con varios hijos: se completan los datos de contacto que traiga la nueva alta
            completar(existente, request);
            return repository.save(existente);
        }).orElseGet(() -> {
            Apoderado nuevo = mapper.toEntity(request);
            nuevo.setTipoDocumento(tipo);
            return repository.save(nuevo);
        });
    }

    @Override
    public Apoderado resolver(Long apoderadoId, ApoderadoRequest datos) {
        if ((apoderadoId == null) == (datos == null)) {
            throw new DatosInvalidosException("Indique el apoderadoId de uno existente o los datos del apoderado, pero no ambos");
        }
        return apoderadoId != null ? buscar(apoderadoId) : crearOReutilizar(datos);
    }

    @Override
    public ApoderadoResponse vincularUsuario(Long id, Long usuarioId) {
        Apoderado apoderado = buscar(id);
        if (repository.existsByUsuarioIdAndIdNot(usuarioId, id)) {
            throw new DuplicateResourceException("El usuario " + usuarioId + " ya está vinculado a otro apoderado");
        }
        apoderado.setUsuarioId(usuarioId);
        return mapper.toResponse(repository.save(apoderado));
    }

    private void completar(Apoderado a, ApoderadoRequest r) {
        if (noVacio(r.telefono())) {
            a.setTelefono(r.telefono());
        }
        if (noVacio(r.telefonoAlterno())) {
            a.setTelefonoAlterno(r.telefonoAlterno());
        }
        if (noVacio(r.email())) {
            a.setEmail(r.email());
        }
        if (noVacio(r.direccion())) {
            a.setDireccion(r.direccion());
        }
        if (noVacio(r.ocupacion())) {
            a.setOcupacion(r.ocupacion());
        }
    }

    private boolean noVacio(String s) {
        return s != null && !s.isBlank();
    }

    private Apoderado buscar(Long id) {
        return repository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Apoderado", id));
    }
}
