package pe.edu.upeu.eduspace.tareas.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upeu.eduspace.tareas.client.PlanificacionClient;
import pe.edu.upeu.eduspace.tareas.client.PlanificacionGateway;
import pe.edu.upeu.eduspace.tareas.config.KafkaConfig;
import pe.edu.upeu.eduspace.tareas.dto.Dtos.*;
import pe.edu.upeu.eduspace.tareas.dto.PageResponse;
import pe.edu.upeu.eduspace.tareas.entity.MatriculaLocal;
import pe.edu.upeu.eduspace.tareas.entity.Tarea;
import pe.edu.upeu.eduspace.tareas.entity.TareaAdjunto;
import pe.edu.upeu.eduspace.tareas.event.TareaPublicadaEvent;
import pe.edu.upeu.eduspace.tareas.exception.BusinessRuleException;
import pe.edu.upeu.eduspace.tareas.exception.ResourceNotFoundException;
import pe.edu.upeu.eduspace.tareas.messaging.TareaEventProducer;
import pe.edu.upeu.eduspace.tareas.repository.EntregaRepository;
import pe.edu.upeu.eduspace.tareas.repository.MatriculaLocalRepository;
import pe.edu.upeu.eduspace.tareas.repository.TareaAdjuntoRepository;
import pe.edu.upeu.eduspace.tareas.repository.TareaRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class TareaService {

    private final TareaRepository tareaRepo;
    private final TareaAdjuntoRepository adjuntoRepo;
    private final EntregaRepository entregaRepo;
    private final MatriculaLocalRepository matriculaRepo;
    private final MatriculaLocalService matriculaLocalService;
    private final PlanificacionGateway planificacion;
    private final ArchivoService archivos;
    private final TareaEventProducer producer;

    // ------------------------------------------------------------------ gestión

    @Transactional
    public TareaResponse crear(TareaCreateRequest req, Long usuarioId, boolean esAdmin) {
        Long docenteId = validarDocente(req.seccionId(), req.cursoId(), usuarioId, esAdmin, null);
        PlanificacionClient.Seccion seccion = planificacion.seccion(req.seccionId());
        PlanificacionClient.Periodo periodo = planificacion.periodo(req.periodoId());
        if (!seccion.anioId().equals(periodo.anioId())) {
            throw new BusinessRuleException("El periodo no pertenece al año de la sección.");
        }
        if (!"ACTIVO".equalsIgnoreCase(periodo.estado())) {
            throw new BusinessRuleException("El periodo '" + periodo.nombre() + "' no está ACTIVO (estado: " + periodo.estado() + ").");
        }
        Tarea t = tareaRepo.save(Tarea.builder()
            .anioId(seccion.anioId()).periodoId(req.periodoId()).seccionId(req.seccionId()).cursoId(req.cursoId())
            .docenteId(docenteId).titulo(req.titulo().trim()).descripcion(req.descripcion())
            .fechaEntrega(req.fechaEntrega())
            .permiteTardia(Boolean.TRUE.equals(req.permiteTardia()))
            .puntajeMaximo(req.puntajeMaximo() != null ? req.puntajeMaximo() : new BigDecimal("20"))
            .estado("BORRADOR").build());
        return toResponse(t);
    }

    /**
     * BORRADOR: se edita todo. PUBLICADA: solo descripción y fecha de entrega; el puntaje, título y política de
     * entrega tardía no cambian una vez publicada (y nunca el puntaje si ya hay notas).
     */
    @Transactional
    public TareaResponse actualizar(Long id, TareaUpdateRequest req, Long usuarioId, boolean esAdmin) {
        Tarea t = obtener(id);
        exigirDueno(t, usuarioId, esAdmin);
        if ("CERRADA".equals(t.getEstado())) {
            throw new BusinessRuleException("Una tarea CERRADA no se puede editar.");
        }
        if ("PUBLICADA".equals(t.getEstado())) {
            boolean cambiaTitulo = req.titulo() != null && !req.titulo().trim().equals(t.getTitulo());
            boolean cambiaTardia = req.permiteTardia() != null && !req.permiteTardia().equals(t.getPermiteTardia());
            boolean cambiaPuntaje = req.puntajeMaximo() != null && req.puntajeMaximo().compareTo(t.getPuntajeMaximo()) != 0;
            if (cambiaTitulo || cambiaTardia || cambiaPuntaje) {
                throw new BusinessRuleException("En una tarea PUBLICADA solo se pueden editar la descripción y la fecha de entrega"
                    + (cambiaPuntaje && entregaRepo.countByTareaIdAndEstado(id, "CALIFICADA") > 0 ? " (el puntaje no cambia: ya hay notas)." : "."));
            }
        } else {
            if (req.titulo() != null) t.setTitulo(req.titulo().trim());
            if (req.permiteTardia() != null) t.setPermiteTardia(req.permiteTardia());
            if (req.puntajeMaximo() != null) t.setPuntajeMaximo(req.puntajeMaximo());
        }
        if (req.descripcion() != null) t.setDescripcion(req.descripcion());
        if (req.fechaEntrega() != null) {
            if ("PUBLICADA".equals(t.getEstado()) && !req.fechaEntrega().isAfter(LocalDateTime.now())) {
                throw new BusinessRuleException("La nueva fecha de entrega debe ser futura.");
            }
            t.setFechaEntrega(req.fechaEntrega());
            t.setRecordatorioEnviado(false);
        }
        t.setActualizadaEn(LocalDateTime.now());
        return toResponse(tareaRepo.save(t));
    }

    @Transactional
    public TareaResponse publicar(Long id, Long usuarioId, boolean esAdmin) {
        Tarea t = obtener(id);
        exigirDueno(t, usuarioId, esAdmin);
        if (!"BORRADOR".equals(t.getEstado())) {
            throw new BusinessRuleException("Solo se puede publicar una tarea en BORRADOR (estado actual: " + t.getEstado() + ").");
        }
        if (!t.getFechaEntrega().isAfter(LocalDateTime.now())) {
            throw new BusinessRuleException("La fecha de entrega debe ser futura para publicar.");
        }
        matriculaLocalService.cargarInicialSiVacia(t.getSeccionId());
        t.setEstado("PUBLICADA");
        t.setPublicadaEn(LocalDateTime.now());
        t.setActualizadaEn(LocalDateTime.now());
        tareaRepo.save(t);
        List<Long> alumnoIds = alumnosDeSeccion(t.getSeccionId());
        producer.publicar(KafkaConfig.TOPIC_TAREA_PUBLICADA, String.valueOf(t.getId()), TareaPublicadaEvent.builder()
            .tareaId(t.getId()).seccionId(t.getSeccionId()).cursoId(t.getCursoId())
            .cursoNombre(planificacion.nombreCursoOpcional(t.getCursoId())).titulo(t.getTitulo())
            .fechaEntrega(t.getFechaEntrega()).alumnoIds(alumnoIds).build());
        log.info("Tarea {} publicada para {} alumno(s)", t.getId(), alumnoIds.size());
        return toResponse(t);
    }

    @Transactional
    public TareaResponse cerrar(Long id, Long usuarioId, boolean esAdmin) {
        Tarea t = obtener(id);
        exigirDueno(t, usuarioId, esAdmin);
        if (!"PUBLICADA".equals(t.getEstado())) {
            throw new BusinessRuleException("Solo se puede cerrar una tarea PUBLICADA.");
        }
        t.setEstado("CERRADA");
        t.setActualizadaEn(LocalDateTime.now());
        return toResponse(tareaRepo.save(t));
    }

    // ------------------------------------------------------------------ material del docente

    @Transactional
    public ArchivoUrlResponse urlAdjunto(Long id, ArchivoUrlRequest req, Long usuarioId, boolean esAdmin) {
        Tarea t = obtener(id);
        exigirDueno(t, usuarioId, esAdmin);
        if ("CERRADA".equals(t.getEstado())) throw new BusinessRuleException("La tarea está CERRADA.");
        archivos.validar(req.contentType(), req.tamanoBytes());
        String ruta = archivos.nuevaRuta("tareas/" + id + "/material/", req.nombreArchivo());
        adjuntoRepo.save(TareaAdjunto.builder().tareaId(id).nombreArchivo(req.nombreArchivo()).rutaStorage(ruta)
            .contentType(req.contentType()).tamanoBytes(req.tamanoBytes()).build());
        return new ArchivoUrlResponse(archivos.urlSubida(ruta), ruta, archivos.expiraMinutos());
    }

    @Transactional(readOnly = true)
    public DescargaResponse urlAdjuntoDescarga(Long tareaId, Long adjuntoId) {
        TareaAdjunto a = adjuntoRepo.findById(adjuntoId).filter(x -> x.getTareaId().equals(tareaId))
            .orElseThrow(() -> new ResourceNotFoundException("Adjunto no encontrado: " + adjuntoId));
        return new DescargaResponse(archivos.urlDescarga(a.getRutaStorage()), a.getNombreArchivo());
    }

    // ------------------------------------------------------------------ consultas

    @Transactional(readOnly = true)
    public PageResponse<TareaResponse> listar(Long seccionId, Long cursoId, Long periodoId, Long docenteId, String estado, Pageable pageable) {
        Page<Tarea> page = tareaRepo.buscar(seccionId, cursoId, periodoId, docenteId,
            estado == null || estado.isBlank() ? null : estado.toUpperCase(), pageable);
        return PageResponse.from(page.map(this::toResponseLiviano));
    }

    @Transactional(readOnly = true)
    public TareaResponse detalle(Long id) {
        return toResponse(obtener(id));
    }

    @Transactional(readOnly = true)
    public Tarea obtener(Long id) {
        return tareaRepo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Tarea no encontrada: " + id));
    }

    /** El docente solo toca sus tareas; ADMIN todas. */
    public void exigirDueno(Tarea t, Long usuarioId, boolean esAdmin) {
        if (esAdmin) return;
        PlanificacionClient.Docente d = planificacion.docentePorUsuario(usuarioId);
        if (d == null || !Objects.equals(d.docenteId(), t.getDocenteId())) {
            throw new AccessDeniedException("La tarea pertenece a otro docente.");
        }
    }

    public List<Long> alumnosDeSeccion(Long seccionId) {
        return matriculaRepo.findBySeccionIdAndEstadoOrderByAlumnoNombre(seccionId, "MATRICULADO").stream()
            .map(MatriculaLocal::getAlumnoId).toList();
    }

    private Long validarDocente(Long seccionId, Long cursoId, Long usuarioId, boolean esAdmin, Long docenteForzado) {
        if (esAdmin) {
            PlanificacionClient.Docente d = planificacion.docentePorUsuario(usuarioId);
            if (d == null) {
                throw new BusinessRuleException("El ADMIN debe tener un docente asociado para crear tareas; use una cuenta de docente.");
            }
            return d.docenteId();
        }
        PlanificacionClient.Docente d = planificacion.docentePorUsuario(usuarioId);
        if (d == null || d.docenteId() == null) {
            throw new AccessDeniedException("El usuario no está registrado como docente.");
        }
        if (!planificacion.asignacionValida(d.docenteId(), seccionId, cursoId)) {
            throw new AccessDeniedException("El docente no está asignado a esta sección y curso.");
        }
        return d.docenteId();
    }

    private TareaResponse toResponseLiviano(Tarea t) {
        return new TareaResponse(t.getId(), t.getAnioId(), t.getPeriodoId(), t.getSeccionId(), t.getCursoId(), t.getDocenteId(),
            t.getTitulo(), t.getDescripcion(), t.getPublicadaEn(), t.getFechaEntrega(), t.getPermiteTardia(),
            t.getPuntajeMaximo(), t.getEstado(), null, null, null, null);
    }

    TareaResponse toResponse(Tarea t) {
        List<AdjuntoResponse> adj = adjuntoRepo.findByTareaIdOrderByIdAsc(t.getId()).stream()
            .map(a -> new AdjuntoResponse(a.getId(), a.getNombreArchivo(), a.getContentType(), a.getTamanoBytes())).toList();
        long entregadas = entregaRepo.countByTareaId(t.getId());
        long calificadas = entregaRepo.countByTareaIdAndEstado(t.getId(), "CALIFICADA");
        int total = matriculaRepo.findBySeccionIdAndEstadoOrderByAlumnoNombre(t.getSeccionId(), "MATRICULADO").size();
        return new TareaResponse(t.getId(), t.getAnioId(), t.getPeriodoId(), t.getSeccionId(), t.getCursoId(), t.getDocenteId(),
            t.getTitulo(), t.getDescripcion(), t.getPublicadaEn(), t.getFechaEntrega(), t.getPermiteTardia(),
            t.getPuntajeMaximo(), t.getEstado(), adj, (int) entregadas, (int) calificadas, (int) Math.max(0, total - entregadas));
    }
}
