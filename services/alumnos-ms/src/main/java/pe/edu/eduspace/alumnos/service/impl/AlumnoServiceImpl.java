package pe.edu.eduspace.alumnos.service.impl;

import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.eduspace.alumnos.dto.*;
import pe.edu.eduspace.alumnos.entity.*;
import pe.edu.eduspace.alumnos.event.AlumnoRegistradoEvent;
import pe.edu.eduspace.alumnos.exception.BusinessRuleException;
import pe.edu.eduspace.alumnos.exception.DuplicateResourceException;
import pe.edu.eduspace.alumnos.exception.ResourceNotFoundException;
import pe.edu.eduspace.alumnos.mapper.AlumnoMapper;
import pe.edu.eduspace.alumnos.repository.AlumnoApoderadoRepository;
import pe.edu.eduspace.alumnos.repository.AlumnoRepository;
import pe.edu.eduspace.alumnos.service.AlumnoService;
import pe.edu.eduspace.alumnos.service.ApoderadoService;
import pe.edu.eduspace.alumnos.util.DocumentoIdentidad;

@Service
@RequiredArgsConstructor
@Transactional
public class AlumnoServiceImpl implements AlumnoService {

    private static final long LOCK_CODIGOS = 7001L;

    private final AlumnoRepository repository;
    private final AlumnoApoderadoRepository vinculoRepository;
    private final ApoderadoService apoderadoService;
    private final AlumnoMapper mapper;
    private final ApplicationEventPublisher eventos;
    private final EntityManager entityManager;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AlumnoResumenResponse> buscar(String q, EstadoAlumno estado, Pageable pageable) {
        String texto = q == null ? "" : q.trim();
        Page<Alumno> page = estado == null ? repository.buscar(texto, pageable)
                : repository.buscarPorEstado(texto, estado, pageable);
        return PageResponse.of(page.map(mapper::toResumen));
    }

    @Override
    @Transactional(readOnly = true)
    public AlumnoResponse obtener(Long id) {
        return respuesta(buscar(id));
    }

    @Override
    public AlumnoResponse crear(AlumnoCreateRequest request) {
        String tipo = DocumentoIdentidad.validar(request.tipoDocumento(), request.numeroDocumento());
        if (repository.existsByTipoDocumentoAndNumeroDocumento(tipo, request.numeroDocumento())) {
            throw new DuplicateResourceException("Ya existe un alumno con " + tipo + " " + request.numeroDocumento());
        }
        List<VinculoApoderadoRequest> vinculos = request.apoderados();
        validarVinculos(vinculos);

        // el correlativo del código se genera bajo un candado de transacción para que dos altas simultáneas no choquen
        entityManager.createNativeQuery("select count(*) from (select pg_advisory_xact_lock(" + LOCK_CODIGOS + ")) t").getSingleResult();
        Alumno alumno = mapper.toEntity(request);
        alumno.setTipoDocumento(tipo);
        alumno.setEstado(EstadoAlumno.ACTIVO);
        alumno.setCodigo(siguienteCodigo());
        alumno = repository.save(alumno);

        List<AlumnoApoderado> creados = new ArrayList<>();
        for (VinculoApoderadoRequest v : vinculos) {
            AlumnoApoderado vinculo = new AlumnoApoderado();
            vinculo.setAlumno(alumno);
            vinculo.setApoderado(apoderadoService.resolver(v.apoderadoId(), v.apoderado()));
            vinculo.setParentesco(v.parentesco());
            vinculo.setEsPrincipal(esPrincipal(v, vinculos.size()));
            vinculo.setEsResponsablePago(Boolean.TRUE.equals(v.esResponsablePago()));
            vinculo.setViveConAlumno(v.viveConAlumno() == null || v.viveConAlumno());
            vinculo.setPuedeRecoger(v.puedeRecoger() == null || v.puedeRecoger());
            creados.add(vinculo);
        }
        vinculoRepository.saveAllAndFlush(creados);

        // el evento solo lleva ids y nombre: nunca documento de identidad, salud ni archivos (privacidad de menores)
        List<Long> apoderadoIds = creados.stream().map(v -> v.getApoderado().getId()).toList();
        List<Long> usuarioIds = new ArrayList<>();
        if (alumno.getUsuarioId() != null) {
            usuarioIds.add(alumno.getUsuarioId());
        }
        creados.stream().map(v -> v.getApoderado().getUsuarioId()).filter(u -> u != null).forEach(usuarioIds::add);
        eventos.publishEvent(AlumnoRegistradoEvent.of(alumno.getId(), alumno.getCodigo(), alumno.nombreCompleto(),
                alumno.getFechaNacimiento(), apoderadoIds, usuarioIds.stream().distinct().toList(), LocalDateTime.now()));
        return mapper.toResponse(alumno, creados.stream().map(mapper::toResponse).toList());
    }

    @Override
    public AlumnoResponse actualizar(Long id, AlumnoUpdateRequest request) {
        Alumno alumno = buscar(id);
        String tipo = DocumentoIdentidad.validar(request.tipoDocumento(), request.numeroDocumento());
        if (repository.existsByTipoDocumentoAndNumeroDocumentoAndIdNot(tipo, request.numeroDocumento(), id)) {
            throw new DuplicateResourceException("Ya existe otro alumno con " + tipo + " " + request.numeroDocumento());
        }
        mapper.update(request, alumno);
        alumno.setTipoDocumento(tipo);
        repository.save(alumno);
        return respuesta(alumno);
    }

    @Override
    public AlumnoResponse vincularUsuario(Long id, Long usuarioId) {
        Alumno alumno = buscar(id);
        if (repository.existsByUsuarioIdAndIdNot(usuarioId, id)) {
            throw new DuplicateResourceException("El usuario " + usuarioId + " ya está vinculado a otro alumno");
        }
        alumno.setUsuarioId(usuarioId);
        repository.save(alumno);
        return respuesta(alumno);
    }

    @Override
    @Transactional(readOnly = true)
    public List<HijoResponse> mios(Long usuarioId, boolean esApoderado) {
        if (usuarioId == null) {
            return List.of();
        }
        if (esApoderado) {
            return vinculoRepository.findByUsuarioDelApoderado(usuarioId).stream().map(mapper::toHijo).toList();
        }
        return repository.findByUsuarioId(usuarioId)
                .map(a -> List.of(new HijoResponse(a.getId(), a.getCodigo(), a.nombreCompleto(), a.getEstado(), null)))
                .orElse(List.of());
    }

    /** Exactamente un principal (si hay un solo apoderado se asume principal), como máximo un responsable de pago, sin repetidos. */
    private void validarVinculos(List<VinculoApoderadoRequest> vinculos) {
        long principales = vinculos.stream().filter(v -> esPrincipal(v, vinculos.size())).count();
        if (principales != 1) {
            throw new BusinessRuleException("El alumno necesita exactamente un apoderado principal (se marcaron " + principales + ")");
        }
        long pagadores = vinculos.stream().filter(v -> Boolean.TRUE.equals(v.esResponsablePago())).count();
        if (pagadores > 1) {
            throw new BusinessRuleException("Solo puede haber un responsable de pago (se marcaron " + pagadores + ")");
        }
        Set<String> claves = new HashSet<>();
        for (VinculoApoderadoRequest v : vinculos) {
            if ((v.apoderadoId() == null) == (v.apoderado() == null)) {
                throw new BusinessRuleException("Cada apoderado debe indicar su apoderadoId o sus datos, pero no ambos");
            }
            String clave = v.apoderadoId() != null ? "id:" + v.apoderadoId()
                    : "doc:" + (v.apoderado().tipoDocumento() == null ? "DNI" : v.apoderado().tipoDocumento().toUpperCase())
                    + ":" + v.apoderado().numeroDocumento();
            if (!claves.add(clave)) {
                throw new BusinessRuleException("El mismo apoderado aparece dos veces en la lista");
            }
        }
    }

    private boolean esPrincipal(VinculoApoderadoRequest v, int total) {
        return Boolean.TRUE.equals(v.esPrincipal()) || (total == 1 && v.esPrincipal() == null);
    }

    private String siguienteCodigo() {
        String prefijo = "AL" + LocalDate.now().getYear() + "-";
        int siguiente = repository.ultimoCodigo(prefijo)
                .map(c -> Integer.parseInt(c.substring(prefijo.length())) + 1).orElse(1);
        return prefijo + String.format("%05d", siguiente);
    }

    private AlumnoResponse respuesta(Alumno alumno) {
        List<VinculoResponse> apoderados = vinculoRepository.findByAlumno(alumno.getId()).stream().map(mapper::toResponse).toList();
        return mapper.toResponse(alumno, apoderados);
    }

    private Alumno buscar(Long id) {
        return repository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Alumno", id));
    }
}
