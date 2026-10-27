package pe.edu.upeu.eduspace.asistencia.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.upeu.eduspace.asistencia.client.PlanificacionClient;
import pe.edu.upeu.eduspace.asistencia.client.PlanificacionGateway;
import pe.edu.upeu.eduspace.asistencia.config.AsistenciaProperties;
import pe.edu.upeu.eduspace.asistencia.dto.Dtos.*;
import pe.edu.upeu.eduspace.asistencia.entity.AsistenciaDetalle;
import pe.edu.upeu.eduspace.asistencia.entity.MatriculaLocal;
import pe.edu.upeu.eduspace.asistencia.entity.SesionAsistencia;
import pe.edu.upeu.eduspace.asistencia.exception.BusinessRuleException;
import pe.edu.upeu.eduspace.asistencia.exception.ResourceNotFoundException;
import pe.edu.upeu.eduspace.asistencia.repository.DetalleRepository;
import pe.edu.upeu.eduspace.asistencia.repository.MatriculaLocalRepository;
import pe.edu.upeu.eduspace.asistencia.repository.SesionRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SesionService {

    private final SesionRepository sesionRepo;
    private final DetalleRepository detalleRepo;
    private final MatriculaLocalRepository matriculaRepo;
    private final MatriculaLocalService matriculaLocalService;
    private final PlanificacionGateway planificacion;
    private final AsistenciaProperties props;
    private final TransactionTemplate tx;

    public SesionService(SesionRepository sesionRepo, DetalleRepository detalleRepo, MatriculaLocalRepository matriculaRepo,
                         MatriculaLocalService matriculaLocalService, PlanificacionGateway planificacion,
                         AsistenciaProperties props, PlatformTransactionManager txm) {
        this.sesionRepo = sesionRepo;
        this.detalleRepo = detalleRepo;
        this.matriculaRepo = matriculaRepo;
        this.matriculaLocalService = matriculaLocalService;
        this.planificacion = planificacion;
        this.props = props;
        this.tx = new TransactionTemplate(txm);
    }

    /**
     * Abre la hoja de asistencia. Idempotente: si ya existe la misma sesión la devuelve.
     * Valida (en este orden): coherencia tipo/curso/bloque, fecha no futura, fecha hábil, docente asignado.
     */
    public SesionResponse abrir(SesionCreateRequest req, Long usuarioId, boolean esAdmin) {
        validarCoherencia(req);
        if (req.fecha().isAfter(LocalDate.now())) {
            throw new BusinessRuleException("No se puede abrir asistencia de una fecha futura.");
        }
        if (!planificacion.esHabil(req.fecha())) {
            throw new BusinessRuleException("La fecha " + req.fecha() + " no es un día hábil (feriado o fin de semana).");
        }
        PlanificacionClient.Seccion seccion = planificacion.seccion(req.seccionId());
        Long docenteId = resolverDocente(req, seccion, usuarioId, esAdmin);

        var existente = sesionRepo.buscarUnica(req.seccionId(), req.fecha(), req.tipo(), req.cursoId(), req.bloqueId());
        if (existente.isPresent()) {
            return hoja(existente.get().getId());
        }
        matriculaLocalService.cargarInicialSiVacia(req.seccionId());
        Long periodoId = planificacion.periodoDeFecha(seccion.anioId(), req.fecha());

        try {
            Long id = tx.execute(status -> {
                SesionAsistencia s = sesionRepo.save(SesionAsistencia.builder()
                    .anioId(seccion.anioId()).periodoId(periodoId).seccionId(req.seccionId()).fecha(req.fecha())
                    .tipo(req.tipo()).cursoId(req.cursoId()).bloqueId(req.bloqueId()).docenteId(docenteId)
                    .abiertaPor(usuarioId).build());
                String defecto = "FALTA".equalsIgnoreCase(props.getEstadoPorDefecto()) ? "FALTA" : "PRESENTE";
                for (MatriculaLocal m : matriculaRepo.findBySeccionIdAndEstadoOrderByAlumnoNombre(req.seccionId(), "MATRICULADO")) {
                    detalleRepo.save(AsistenciaDetalle.builder().sesion(s).matriculaId(m.getMatriculaId())
                        .alumnoId(m.getAlumnoId()).estado(defecto).build());
                }
                return s.getId();
            });
            return hoja(id);
        } catch (DataIntegrityViolationException e) {
            // Dos aperturas simultáneas: gana la primera, la otra devuelve esa misma hoja
            return hoja(sesionRepo.buscarUnica(req.seccionId(), req.fecha(), req.tipo(), req.cursoId(), req.bloqueId())
                .orElseThrow(() -> e).getId());
        }
    }

    @Transactional(readOnly = true)
    public SesionResponse hoja(Long sesionId) {
        SesionAsistencia s = obtener(sesionId);
        List<AsistenciaDetalle> detalles = detalleRepo.findBySesionIdOrderByIdAsc(sesionId);
        Map<Long, MatriculaLocal> matriculas = matriculaRepo.findAllById(detalles.stream().map(AsistenciaDetalle::getMatriculaId).toList())
            .stream().collect(Collectors.toMap(MatriculaLocal::getMatriculaId, Function.identity()));
        List<DetalleResponse> alumnos = detalles.stream()
            .map(d -> new DetalleResponse(d.getId(), d.getMatriculaId(), d.getAlumnoId(),
                matriculas.containsKey(d.getMatriculaId()) ? matriculas.get(d.getMatriculaId()).getAlumnoNombre() : "Alumno " + d.getAlumnoId(),
                d.getEstado(), d.getMinutosTarde(), d.getObservacion(), Boolean.TRUE.equals(d.getConfirmado())))
            .sorted(java.util.Comparator.comparing(DetalleResponse::alumnoNombre))
            .toList();
        return new SesionResponse(s.getId(), s.getAnioId(), s.getPeriodoId(), s.getSeccionId(), s.getFecha(), s.getTipo(),
            s.getCursoId(), s.getBloqueId(), s.getDocenteId(), s.getEstado(), s.getCerradaEn(), alumnos);
    }

    @Transactional(readOnly = true)
    public List<SesionResponse> buscar(Long seccionId, LocalDate fecha) {
        return sesionRepo.findBySeccionIdAndFechaOrderByTipoAscIdAsc(seccionId, fecha).stream().map(s -> hoja(s.getId())).toList();
    }

    @Transactional(readOnly = true)
    public SesionAsistencia obtener(Long id) {
        return sesionRepo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Sesión de asistencia no encontrada: " + id));
    }

    /** El docente solo opera sesiones propias; ADMIN puede todas. */
    public void exigirDocenteDe(SesionAsistencia s, Long usuarioId, boolean esAdmin) {
        if (esAdmin) return;
        PlanificacionClient.Docente d = planificacion.docentePorUsuario(usuarioId);
        if (d == null || !d.docenteId().equals(s.getDocenteId())) {
            throw new AccessDeniedException("La sesión pertenece a otro docente.");
        }
    }

    private Long resolverDocente(SesionCreateRequest req, PlanificacionClient.Seccion seccion, Long usuarioId, boolean esAdmin) {
        if (esAdmin) {
            Long docenteId = req.docenteId() != null ? req.docenteId() : seccion.tutorDocenteId();
            if (docenteId == null) {
                throw new BusinessRuleException("Indique docenteId: la sección no tiene tutor y el ADMIN no es docente.");
            }
            return docenteId;
        }
        PlanificacionClient.Docente d = planificacion.docentePorUsuario(usuarioId);
        if (d == null || d.docenteId() == null) {
            throw new AccessDeniedException("El usuario no está registrado como docente.");
        }
        boolean asignado;
        if ("CURSO".equals(req.tipo())) {
            asignado = planificacion.asignacionValida(d.docenteId(), req.seccionId(), req.cursoId());
        } else {
            asignado = d.docenteId().equals(seccion.tutorDocenteId())
                || planificacion.asignaciones(usuarioId, seccion.anioId()).stream().anyMatch(a -> req.seccionId().equals(a.seccionId()));
        }
        if (!asignado) {
            throw new AccessDeniedException("El docente no está asignado a esta sección" + ("CURSO".equals(req.tipo()) ? " y curso." : "."));
        }
        return d.docenteId();
    }

    private void validarCoherencia(SesionCreateRequest req) {
        if ("DIA".equals(req.tipo()) && (req.cursoId() != null || req.bloqueId() != null)) {
            throw new BusinessRuleException("Una sesión DIA no lleva cursoId ni bloqueId.");
        }
        if ("CURSO".equals(req.tipo()) && (req.cursoId() == null || req.bloqueId() == null)) {
            throw new BusinessRuleException("Una sesión CURSO requiere cursoId y bloqueId.");
        }
    }
}
