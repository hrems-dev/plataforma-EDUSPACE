package pe.edu.upeu.eduspace.asistencia.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upeu.eduspace.asistencia.config.KafkaConfig;
import pe.edu.upeu.eduspace.asistencia.dto.Dtos.*;
import pe.edu.upeu.eduspace.asistencia.dto.PageResponse;
import pe.edu.upeu.eduspace.asistencia.entity.AsistenciaAuditoria;
import pe.edu.upeu.eduspace.asistencia.entity.AsistenciaDetalle;
import pe.edu.upeu.eduspace.asistencia.entity.Justificacion;
import pe.edu.upeu.eduspace.asistencia.entity.MatriculaLocal;
import pe.edu.upeu.eduspace.asistencia.event.JustificacionResueltaEvent;
import pe.edu.upeu.eduspace.asistencia.exception.BusinessRuleException;
import pe.edu.upeu.eduspace.asistencia.exception.ResourceNotFoundException;
import pe.edu.upeu.eduspace.asistencia.messaging.AsistenciaEventProducer;
import pe.edu.upeu.eduspace.asistencia.repository.AuditoriaRepository;
import pe.edu.upeu.eduspace.asistencia.repository.DetalleRepository;
import pe.edu.upeu.eduspace.asistencia.repository.JustificacionRepository;
import pe.edu.upeu.eduspace.asistencia.repository.MatriculaLocalRepository;
import pe.edu.upeu.eduspace.asistencia.security.IdentidadService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class JustificacionService {

    private static final int MAX_DIAS_RANGO = 31;

    private final JustificacionRepository repo;
    private final DetalleRepository detalleRepo;
    private final AuditoriaRepository auditoriaRepo;
    private final MatriculaLocalRepository matriculaRepo;
    private final AsistenciaEventProducer producer;
    private final ArchivoService archivos;
    private final IdentidadService identidad;

    /** El apoderado solo puede justificar a sus hijos. */
    @Transactional
    public JustificacionResponse solicitar(JustificacionRequest req, org.springframework.security.oauth2.jwt.Jwt jwt) {
        Long usuarioId = identidad.usuarioId(jwt);
        identidad.exigirAcceso(jwt, req.alumnoId());
        if (req.fechaHasta().isBefore(req.fechaDesde())) {
            throw new BusinessRuleException("fechaHasta no puede ser anterior a fechaDesde.");
        }
        if (req.fechaDesde().plusDays(MAX_DIAS_RANGO).isBefore(req.fechaHasta())) {
            throw new BusinessRuleException("El rango de una justificación no puede superar " + MAX_DIAS_RANGO + " días.");
        }
        MatriculaLocal m = matriculaRepo.findFirstByAlumnoIdAndEstadoOrderByAnioIdDesc(req.alumnoId(), "MATRICULADO")
            .orElseThrow(() -> new BusinessRuleException("El alumno no tiene una matrícula vigente."));
        Justificacion j = repo.save(Justificacion.builder().matriculaId(m.getMatriculaId()).alumnoId(req.alumnoId())
            .fechaDesde(req.fechaDesde()).fechaHasta(req.fechaHasta()).tipo(req.tipo()).motivo(req.motivo())
            .solicitadoPor(usuarioId).build());
        return toResponse(j, 0);
    }

    @Transactional
    public ArchivoUrlResponse urlArchivo(Long id, ArchivoUrlRequest req, org.springframework.security.oauth2.jwt.Jwt jwt) {
        Justificacion j = obtener(id);
        if (!j.getSolicitadoPor().equals(identidad.usuarioId(jwt))) {
            throw new AccessDeniedException("Solo quien solicitó la justificación puede adjuntar el archivo.");
        }
        if (!"PENDIENTE".equals(j.getEstado())) {
            throw new BusinessRuleException("La justificación ya fue resuelta; no admite archivos.");
        }
        ArchivoUrlResponse r = archivos.urlSubida(id, req.nombreArchivo(), req.contentType());
        j.setRutaArchivo(r.ruta());
        repo.save(j);
        return r;
    }

    @Transactional(readOnly = true)
    public String urlDescarga(Long id, org.springframework.security.oauth2.jwt.Jwt jwt) {
        Justificacion j = obtener(id);
        identidad.exigirAcceso(jwt, j.getAlumnoId());
        if (j.getRutaArchivo() == null) throw new ResourceNotFoundException("La justificación no tiene archivo adjunto.");
        return archivos.urlDescarga(j.getRutaArchivo());
    }

    @Transactional(readOnly = true)
    public PageResponse<JustificacionResponse> bandeja(String estado, Pageable pageable) {
        Page<Justificacion> page = estado == null || estado.isBlank()
            ? repo.findAllByOrderByCreadoEnDesc(pageable)
            : repo.findByEstadoOrderByCreadoEnAsc(estado.toUpperCase(), pageable);
        return PageResponse.from(page.map(j -> toResponse(j, 0)));
    }

    @Transactional(readOnly = true)
    public java.util.List<JustificacionResponse> delosAlumnos(Set<Long> alumnoIds) {
        if (alumnoIds.isEmpty()) return java.util.List.of();
        return repo.findByAlumnoIdInOrderByCreadoEnDesc(alumnoIds).stream().map(j -> toResponse(j, 0)).toList();
    }

    /** Aprobar convierte exactamente las FALTAS del rango en JUSTIFICADO (con auditoría) y avisa al apoderado. */
    @Transactional
    public JustificacionResponse resolver(Long id, ResolverJustificacionRequest req, Long usuarioId) {
        Justificacion j = obtener(id);
        if (!"PENDIENTE".equals(j.getEstado())) {
            throw new BusinessRuleException("La justificación ya fue " + j.getEstado().toLowerCase() + ".");
        }
        int convertidas = 0;
        if ("APROBADA".equals(req.resolucion())) {
            for (AsistenciaDetalle d : detalleRepo.faltasDeMatriculaEnRango(j.getMatriculaId(), j.getFechaDesde(), j.getFechaHasta())) {
                d.setEstado("JUSTIFICADO");
                d.setMinutosTarde(null);
                d.setActualizadoEn(LocalDateTime.now());
                detalleRepo.save(d);
                auditoriaRepo.save(AsistenciaAuditoria.builder().detalleId(d.getId()).estadoAnterior("FALTA")
                    .estadoNuevo("JUSTIFICADO").motivo("Justificación " + j.getId() + " aprobada").usuarioId(usuarioId).build());
                convertidas++;
            }
        }
        j.setEstado(req.resolucion());
        j.setResueltoPor(usuarioId);
        j.setResueltoEn(LocalDateTime.now());
        j.setComentarioResolucion(req.comentario());
        repo.save(j);
        producer.publicar(KafkaConfig.TOPIC_JUSTIFICACION_RESUELTA, String.valueOf(j.getId()),
            JustificacionResueltaEvent.builder().justificacionId(j.getId()).alumnoId(j.getAlumnoId())
                .matriculaId(j.getMatriculaId()).resultado(req.resolucion())
                .fechaDesde(j.getFechaDesde()).fechaHasta(j.getFechaHasta()).build());
        log.info("Justificación {} {} ({} falta(s) justificadas)", id, req.resolucion(), convertidas);
        return toResponse(j, convertidas);
    }

    private Justificacion obtener(Long id) {
        return repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Justificación no encontrada: " + id));
    }

    private JustificacionResponse toResponse(Justificacion j, int convertidas) {
        return new JustificacionResponse(j.getId(), j.getMatriculaId(), j.getAlumnoId(), j.getFechaDesde(), j.getFechaHasta(),
            j.getTipo(), j.getMotivo(), j.getRutaArchivo(), j.getEstado(), j.getSolicitadoPor(), j.getResueltoPor(),
            j.getResueltoEn(), j.getComentarioResolucion(), j.getCreadoEn(), convertidas);
    }
}
