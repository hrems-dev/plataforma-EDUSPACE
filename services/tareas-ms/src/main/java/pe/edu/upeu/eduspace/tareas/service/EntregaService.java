package pe.edu.upeu.eduspace.tareas.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upeu.eduspace.tareas.config.KafkaConfig;
import pe.edu.upeu.eduspace.tareas.config.TareasProperties;
import pe.edu.upeu.eduspace.tareas.dto.Dtos.*;
import pe.edu.upeu.eduspace.tareas.entity.Entrega;
import pe.edu.upeu.eduspace.tareas.entity.EntregaArchivo;
import pe.edu.upeu.eduspace.tareas.entity.MatriculaLocal;
import pe.edu.upeu.eduspace.tareas.entity.Tarea;
import pe.edu.upeu.eduspace.tareas.event.TareaEntregadaEvent;
import pe.edu.upeu.eduspace.tareas.exception.BusinessRuleException;
import pe.edu.upeu.eduspace.tareas.exception.ResourceNotFoundException;
import pe.edu.upeu.eduspace.tareas.messaging.TareaEventProducer;
import pe.edu.upeu.eduspace.tareas.repository.EntregaArchivoRepository;
import pe.edu.upeu.eduspace.tareas.repository.EntregaRepository;
import pe.edu.upeu.eduspace.tareas.repository.MatriculaLocalRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class EntregaService {

    private final TareaService tareaService;
    private final EntregaRepository entregaRepo;
    private final EntregaArchivoRepository archivoRepo;
    private final MatriculaLocalRepository matriculaRepo;
    private final MatriculaLocalService matriculaLocalService;
    private final ArchivoService archivos;
    private final TareasProperties props;
    private final TareaEventProducer producer;

    /** URL firmada de subida; la ruta incluye tarea y alumno para poder verificar luego que el archivo es suyo. */
    @Transactional(readOnly = true)
    public ArchivoUrlResponse urlSubida(Long tareaId, ArchivoUrlRequest req, Long alumnoId) {
        Tarea t = tareaService.obtener(tareaId);
        matriculaDelAlumno(t, alumnoId);
        if (!"PUBLICADA".equals(t.getEstado())) throw new BusinessRuleException("La tarea no admite entregas (estado " + t.getEstado() + ").");
        archivos.validar(req.contentType(), req.tamanoBytes());
        String ruta = archivos.nuevaRuta(prefijo(tareaId, alumnoId), req.nombreArchivo());
        return new ArchivoUrlResponse(archivos.urlSubida(ruta), ruta, archivos.expiraMinutos());
    }

    /**
     * Registra o reenvía la entrega. Valida matrícula de la sección, estado de la tarea, plazo (tardía si la tarea
     * lo permite) y que cada archivo declarado esté bajo el prefijo del propio alumno. Reenviar sube la versión
     * mientras no esté CALIFICADA.
     */
    @Transactional
    public EntregaResponse registrar(Long tareaId, EntregaRequest req, Long alumnoId) {
        Tarea t = tareaService.obtener(tareaId);
        MatriculaLocal m = matriculaDelAlumno(t, alumnoId);
        if (!"PUBLICADA".equals(t.getEstado())) {
            throw new BusinessRuleException("La tarea no admite entregas (estado " + t.getEstado() + ").");
        }
        LocalDateTime ahora = LocalDateTime.now();
        boolean tardia = ahora.isAfter(t.getFechaEntrega());
        if (tardia && !Boolean.TRUE.equals(t.getPermiteTardia())) {
            throw new BusinessRuleException("El plazo de entrega venció y la tarea no admite entregas tardías.");
        }
        List<ArchivoRef> refs = req.archivos() == null ? List.of() : req.archivos();
        boolean sinComentario = req.comentario() == null || req.comentario().isBlank();
        if (refs.isEmpty() && sinComentario) {
            throw new BusinessRuleException("La entrega debe incluir al menos un archivo o un comentario.");
        }
        validarArchivos(refs, tareaId, alumnoId);

        Entrega e = entregaRepo.findByTareaIdAndMatriculaId(tareaId, m.getMatriculaId()).orElse(null);
        if (e != null && "CALIFICADA".equals(e.getEstado())) {
            throw new BusinessRuleException("La entrega ya fue calificada; no admite reenvío.");
        }
        if (e == null) {
            e = Entrega.builder().tareaId(tareaId).matriculaId(m.getMatriculaId()).alumnoId(alumnoId).version((short) 1).build();
        } else {
            e.setVersion((short) (e.getVersion() + 1));
            archivoRepo.deleteByEntregaId(e.getId());
        }
        e.setComentario(req.comentario());
        e.setEntregadaEn(ahora);
        e.setEsTardia(tardia);
        e.setEstado("ENTREGADA");
        e = entregaRepo.save(e);
        for (ArchivoRef r : refs) {
            archivoRepo.save(EntregaArchivo.builder().entregaId(e.getId()).nombreArchivo(r.nombreArchivo())
                .rutaStorage(r.ruta()).contentType(r.contentType()).tamanoBytes(r.tamanoBytes()).build());
        }
        producer.publicar(KafkaConfig.TOPIC_TAREA_ENTREGADA, String.valueOf(e.getId()), TareaEntregadaEvent.builder()
            .tareaId(tareaId).entregaId(e.getId()).alumnoId(alumnoId).matriculaId(m.getMatriculaId())
            .seccionId(t.getSeccionId()).version(e.getVersion()).esTardia(e.getEsTardia()).build());
        return toResponse(e, m.getAlumnoNombre());
    }

    /** Docente: todos los alumnos de la sección con el estado de su entrega (incluye los que no entregaron). */
    @Transactional(readOnly = true)
    public List<EntregaAlumno> deLaTarea(Long tareaId, Long usuarioId, boolean esAdmin) {
        Tarea t = tareaService.obtener(tareaId);
        tareaService.exigirDueno(t, usuarioId, esAdmin);
        matriculaLocalService.cargarInicialSiVacia(t.getSeccionId());
        var porMatricula = new java.util.HashMap<Long, Entrega>();
        entregaRepo.findByTareaIdOrderByEntregadaEnAsc(tareaId).forEach(x -> porMatricula.put(x.getMatriculaId(), x));
        List<EntregaAlumno> res = new ArrayList<>();
        for (MatriculaLocal m : matriculaRepo.findBySeccionIdAndEstadoOrderByAlumnoNombre(t.getSeccionId(), "MATRICULADO")) {
            Entrega e = porMatricula.get(m.getMatriculaId());
            res.add(new EntregaAlumno(m.getMatriculaId(), m.getAlumnoId(), m.getAlumnoNombre(),
                e == null ? "SIN_ENTREGAR" : e.getEstado(), e == null ? null : toResponse(e, m.getAlumnoNombre())));
        }
        return res;
    }

    @Transactional(readOnly = true)
    public EntregaResponse obtenerEntrega(Long entregaId) {
        Entrega e = obtener(entregaId);
        String nombre = matriculaRepo.findById(e.getMatriculaId()).map(MatriculaLocal::getAlumnoNombre).orElse("Alumno " + e.getAlumnoId());
        return toResponse(e, nombre);
    }

    /** URL de descarga de un archivo de la entrega: docente de la tarea o dueño (alumno / su apoderado). */
    @Transactional(readOnly = true)
    public DescargaResponse urlArchivo(Long entregaId, Long archivoId, Long usuarioId, boolean esAdmin,
                                       Set<Long> alumnosDelUsuario, boolean esDocente) {
        Entrega e = obtener(entregaId);
        Tarea t = tareaService.obtener(e.getTareaId());
        if (esDocente || esAdmin) {
            tareaService.exigirDueno(t, usuarioId, esAdmin);
        } else if (!alumnosDelUsuario.contains(e.getAlumnoId())) {
            throw new AccessDeniedException("No puede descargar archivos de esta entrega.");
        }
        EntregaArchivo a = archivoRepo.findById(archivoId).filter(x -> x.getEntregaId().equals(entregaId))
            .orElseThrow(() -> new ResourceNotFoundException("Archivo no encontrado: " + archivoId));
        return new DescargaResponse(archivos.urlDescarga(a.getRutaStorage()), a.getNombreArchivo());
    }

    /** ¿Alguno de los alumnos está matriculado en la sección? */
    @Transactional(readOnly = true)
    public boolean deSeccion(Long seccionId, Set<Long> alumnoIds) {
        return matriculaRepo.findBySeccionIdAndEstadoOrderByAlumnoNombre(seccionId, "MATRICULADO").stream()
            .anyMatch(m -> alumnoIds.contains(m.getAlumnoId()));
    }

    Entrega obtener(Long id) {
        return entregaRepo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Entrega no encontrada: " + id));
    }

    EntregaResponse toResponse(Entrega e, String alumnoNombre) {
        List<ArchivoResponse> arch = archivoRepo.findByEntregaIdOrderByIdAsc(e.getId()).stream()
            .map(a -> new ArchivoResponse(a.getId(), a.getNombreArchivo(), a.getContentType(), a.getTamanoBytes())).toList();
        return new EntregaResponse(e.getId(), e.getTareaId(), e.getMatriculaId(), e.getAlumnoId(), alumnoNombre, e.getComentario(),
            e.getEntregadaEn(), e.getEsTardia(), e.getVersion(), e.getEstado(), e.getNota(), e.getRetroalimentacion(),
            e.getCalificadaEn(), arch);
    }

    private MatriculaLocal matriculaDelAlumno(Tarea t, Long alumnoId) {
        matriculaLocalService.cargarInicialSiVacia(t.getSeccionId());
        return matriculaRepo.findBySeccionIdAndEstadoOrderByAlumnoNombre(t.getSeccionId(), "MATRICULADO").stream()
            .filter(m -> m.getAlumnoId().equals(alumnoId)).findFirst()
            .orElseThrow(() -> new BusinessRuleException("El alumno no está matriculado en la sección de esta tarea."));
    }

    private void validarArchivos(List<ArchivoRef> refs, Long tareaId, Long alumnoId) {
        if (refs.size() > props.getMaxArchivosEntrega()) {
            throw new BusinessRuleException("Máximo " + props.getMaxArchivosEntrega() + " archivos por entrega.");
        }
        String prefijo = prefijo(tareaId, alumnoId);
        long total = 0;
        for (ArchivoRef r : refs) {
            if (!r.ruta().startsWith(prefijo)) {
                throw new BusinessRuleException("El archivo '" + r.nombreArchivo() + "' no fue subido con una URL firmada de esta entrega.");
            }
            archivos.validar(r.contentType(), r.tamanoBytes());
            total += r.tamanoBytes();
        }
        if (total > props.getEntregaMaxMb() * 1024L * 1024L) {
            throw new BusinessRuleException("La entrega excede el tamaño máximo de " + props.getEntregaMaxMb() + " MB.");
        }
    }

    private static String prefijo(Long tareaId, Long alumnoId) {
        return "tareas/" + tareaId + "/entregas/" + alumnoId + "/";
    }
}
