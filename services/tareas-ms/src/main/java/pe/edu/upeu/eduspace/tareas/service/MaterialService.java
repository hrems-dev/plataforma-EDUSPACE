package pe.edu.upeu.eduspace.tareas.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upeu.eduspace.tareas.client.PlanificacionClient;
import pe.edu.upeu.eduspace.tareas.client.PlanificacionGateway;
import pe.edu.upeu.eduspace.tareas.config.KafkaConfig;
import pe.edu.upeu.eduspace.tareas.dto.Dtos.*;
import pe.edu.upeu.eduspace.tareas.entity.Material;
import pe.edu.upeu.eduspace.tareas.event.MaterialPublicadoEvent;
import pe.edu.upeu.eduspace.tareas.exception.BusinessRuleException;
import pe.edu.upeu.eduspace.tareas.exception.ResourceNotFoundException;
import pe.edu.upeu.eduspace.tareas.messaging.TareaEventProducer;
import pe.edu.upeu.eduspace.tareas.repository.MaterialRepository;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Materiales de curso. Cada docente sube archivos o enlaces de SUS cursos (el que tiene asignado en esa sección); los alumnos y
 * apoderados de la sección los ven y los descargan con una URL firmada de corta duración. Al publicar se avisa a la familia.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MaterialService {

    private final MaterialRepository repo;
    private final PlanificacionGateway planificacion;
    private final ArchivoService archivos;
    private final TareaService tareaService;
    private final EntregaService entregaService;
    private final MatriculaLocalService matriculaLocalService;
    private final TareaEventProducer producer;

    private static String prefijo(Long seccionId, Long cursoId) {
        return "materiales/" + seccionId + "/" + cursoId + "/";
    }

    /** Paso 1 (archivo): valida tipo y tamaño y entrega la URL firmada para subirlo. */
    public ArchivoUrlResponse urlSubida(MaterialArchivoUrlRequest req, Long usuarioId, boolean esAdmin) {
        validarDocente(req.seccionId(), req.cursoId(), usuarioId, esAdmin);
        archivos.validar(req.contentType(), req.tamanoBytes());
        String ruta = archivos.nuevaRuta(prefijo(req.seccionId(), req.cursoId()), req.nombreArchivo());
        return new ArchivoUrlResponse(archivos.urlSubida(ruta), ruta, archivos.expiraMinutos());
    }

    /** Paso 2: registra el material (archivo ya subido o enlace) y avisa a la familia. */
    @Transactional
    public MaterialResponse crear(MaterialRequest req, Long usuarioId, boolean esAdmin) {
        Long docenteId = validarDocente(req.seccionId(), req.cursoId(), usuarioId, esAdmin);
        PlanificacionClient.Seccion seccion = planificacion.seccion(req.seccionId());
        Material.MaterialBuilder m = Material.builder().anioId(seccion.anioId()).seccionId(req.seccionId()).cursoId(req.cursoId())
            .docenteId(docenteId).titulo(req.titulo().trim()).descripcion(vacioANulo(req.descripcion()));
        String tipo = req.tipo() == null ? "" : req.tipo().toUpperCase();
        if ("ENLACE".equals(tipo)) {
            String url = req.enlace() == null ? "" : req.enlace().trim();
            if (!(url.startsWith("http://") || url.startsWith("https://")) || url.length() < 10) {
                throw new BusinessRuleException("El enlace debe empezar con http:// o https://");
            }
            m.tipo("ENLACE").enlace(url);
        } else if ("ARCHIVO".equals(tipo)) {
            ArchivoRef a = req.archivo();
            if (a == null) throw new BusinessRuleException("Falta el archivo del material.");
            if (!a.ruta().startsWith(prefijo(req.seccionId(), req.cursoId()))) {
                throw new BusinessRuleException("El archivo no corresponde a este curso. Súbelo de nuevo.");
            }
            archivos.validar(a.contentType(), a.tamanoBytes());
            m.tipo("ARCHIVO").nombreArchivo(a.nombreArchivo()).rutaStorage(a.ruta()).contentType(a.contentType()).tamanoBytes(a.tamanoBytes());
        } else {
            throw new BusinessRuleException("El tipo debe ser ARCHIVO o ENLACE.");
        }
        Material guardado = repo.save(m.build());

        matriculaLocalService.cargarInicialSiVacia(req.seccionId());
        List<Long> alumnoIds = tareaService.alumnosDeSeccion(req.seccionId());
        producer.publicar(KafkaConfig.TOPIC_MATERIAL_PUBLICADO, String.valueOf(guardado.getId()), MaterialPublicadoEvent.builder()
            .materialId(guardado.getId()).seccionId(req.seccionId()).cursoId(req.cursoId())
            .cursoNombre(planificacion.nombreCursoOpcional(req.cursoId())).titulo(guardado.getTitulo()).alumnoIds(alumnoIds).build());
        log.info("Material {} publicado en sección {} curso {} ({} alumno(s))", guardado.getId(), req.seccionId(), req.cursoId(), alumnoIds.size());
        return toResponse(guardado);
    }

    /** Lista los materiales de un curso en una sección: personal siempre; familias solo si tienen un alumno en esa sección. */
    @Transactional(readOnly = true)
    public List<MaterialResponse> listar(Long seccionId, Long cursoId, boolean esStaff, Set<Long> alumnosDelUsuario) {
        exigirParticipante(seccionId, esStaff, alumnosDelUsuario);
        return repo.findBySeccionIdAndCursoIdOrderByCreadoEnDescIdDesc(seccionId, cursoId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public DescargaResponse urlDescarga(Long id, boolean esStaff, Set<Long> alumnosDelUsuario) {
        Material m = obtener(id);
        exigirParticipante(m.getSeccionId(), esStaff, alumnosDelUsuario);
        if (!"ARCHIVO".equals(m.getTipo())) throw new BusinessRuleException("Este material es un enlace, no un archivo.");
        return new DescargaResponse(archivos.urlDescarga(m.getRutaStorage()), m.getNombreArchivo());
    }

    @Transactional
    public void eliminar(Long id, Long usuarioId, boolean esAdmin) {
        Material m = obtener(id);
        if (!esAdmin) {
            PlanificacionClient.Docente d = planificacion.docentePorUsuario(usuarioId);
            if (d == null || !Objects.equals(d.docenteId(), m.getDocenteId())) throw new AccessDeniedException("El material pertenece a otro docente.");
        }
        repo.delete(m);
        if ("ARCHIVO".equals(m.getTipo()) && m.getRutaStorage() != null) archivos.eliminar(m.getRutaStorage());
    }

    // ------------------------------------------------------------------ utilidades

    private Material obtener(Long id) {
        return repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Material no encontrado: " + id));
    }

    private void exigirParticipante(Long seccionId, boolean esStaff, Set<Long> alumnosDelUsuario) {
        if (esStaff) return;
        if (alumnosDelUsuario == null || !entregaService.deSeccion(seccionId, alumnosDelUsuario)) {
            throw new AccessDeniedException("No participa en este curso.");
        }
    }

    /** El docente solo sube material de las secciones y cursos que tiene asignados; el ADMIN debe tener un docente asociado. */
    private Long validarDocente(Long seccionId, Long cursoId, Long usuarioId, boolean esAdmin) {
        PlanificacionClient.Docente d = planificacion.docentePorUsuario(usuarioId);
        if (esAdmin) {
            if (d == null) throw new BusinessRuleException("El ADMIN debe tener un docente asociado para subir materiales; use una cuenta de docente.");
            return d.docenteId();
        }
        if (d == null || d.docenteId() == null) throw new AccessDeniedException("El usuario no está registrado como docente.");
        if (!planificacion.asignacionValida(d.docenteId(), seccionId, cursoId)) {
            throw new AccessDeniedException("El docente no está asignado a esta sección y curso.");
        }
        return d.docenteId();
    }

    private static String vacioANulo(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private MaterialResponse toResponse(Material m) {
        return new MaterialResponse(m.getId(), m.getSeccionId(), m.getCursoId(), m.getDocenteId(), m.getTitulo(), m.getDescripcion(), m.getTipo(),
            m.getEnlace(), m.getNombreArchivo(), m.getContentType(), m.getTamanoBytes(), m.getCreadoEn());
    }
}
