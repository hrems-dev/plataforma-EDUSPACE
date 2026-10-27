package pe.edu.upeu.eduspace.tareas.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import pe.edu.upeu.eduspace.tareas.dto.Dtos.*;
import pe.edu.upeu.eduspace.tareas.dto.PageResponse;
import pe.edu.upeu.eduspace.tareas.entity.Tarea;
import pe.edu.upeu.eduspace.tareas.security.IdentidadService;
import pe.edu.upeu.eduspace.tareas.service.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/tareas")
@RequiredArgsConstructor
@Tag(name = "Tareas", description = "Publicación, entregas y calificación de tareas")
public class TareaController {

    private final TareaService tareaService;
    private final EntregaService entregaService;
    private final CalificacionService calificacionService;
    private final PendientesService pendientesService;
    private final IdentidadService identidad;

    // ---------------------------------------------------------------- gestión (docente)

    @PostMapping
    @Operation(summary = "Crear tarea en BORRADOR (DOCENTE asignado)")
    public ResponseEntity<TareaResponse> crear(@Valid @RequestBody TareaCreateRequest req, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tareaService.crear(req, identidad.usuarioId(jwt), identidad.esAdmin(jwt)));
    }

    @PutMapping("/{id}")
    public TareaResponse actualizar(@PathVariable Long id, @Valid @RequestBody TareaUpdateRequest req, @AuthenticationPrincipal Jwt jwt) {
        return tareaService.actualizar(id, req, identidad.usuarioId(jwt), identidad.esAdmin(jwt));
    }

    @PatchMapping("/{id}/publicar")
    public TareaResponse publicar(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return tareaService.publicar(id, identidad.usuarioId(jwt), identidad.esAdmin(jwt));
    }

    @PatchMapping("/{id}/cerrar")
    public TareaResponse cerrar(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return tareaService.cerrar(id, identidad.usuarioId(jwt), identidad.esAdmin(jwt));
    }

    @PostMapping("/{id}/adjuntos/url")
    @Operation(summary = "URL firmada para subir material de la tarea (DOCENTE)")
    public ArchivoUrlResponse urlAdjunto(@PathVariable Long id, @Valid @RequestBody ArchivoUrlRequest req, @AuthenticationPrincipal Jwt jwt) {
        return tareaService.urlAdjunto(id, req, identidad.usuarioId(jwt), identidad.esAdmin(jwt));
    }

    @GetMapping("/{id}/adjuntos/{adjuntoId}/url")
    public DescargaResponse descargarAdjunto(@PathVariable Long id, @PathVariable Long adjuntoId, @AuthenticationPrincipal Jwt jwt) {
        exigirParticipante(tareaService.obtener(id), jwt);
        return tareaService.urlAdjuntoDescarga(id, adjuntoId);
    }

    @GetMapping
    @Operation(summary = "Listar tareas (DOCENTE, SECRETARIA, ADMIN)")
    public PageResponse<TareaResponse> listar(@RequestParam(required = false) Long seccionId,
                                              @RequestParam(required = false) Long cursoId,
                                              @RequestParam(required = false) Long periodoId,
                                              @RequestParam(required = false) String estado,
                                              Pageable pageable) {
        return tareaService.listar(seccionId, cursoId, periodoId, null, estado, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalle con adjuntos y contadores (participantes)")
    public TareaResponse detalle(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        Tarea t = tareaService.obtener(id);
        exigirParticipante(t, jwt);
        return tareaService.detalle(id);
    }

    // ---------------------------------------------------------------- entregas

    @PostMapping("/{id}/entregas/archivo-url")
    @Operation(summary = "URL firmada de subida de un archivo de la entrega (ALUMNO)")
    public ArchivoUrlResponse urlEntrega(@PathVariable Long id, @Valid @RequestBody ArchivoUrlRequest req, @AuthenticationPrincipal Jwt jwt) {
        return entregaService.urlSubida(id, req, alumnoPropio(jwt));
    }

    @PostMapping("/{id}/entregas")
    @Operation(summary = "Registrar o reenviar la entrega (ALUMNO)")
    public ResponseEntity<EntregaResponse> entregar(@PathVariable Long id, @Valid @RequestBody EntregaRequest req, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.status(HttpStatus.CREATED).body(entregaService.registrar(id, req, alumnoPropio(jwt)));
    }

    @GetMapping("/{id}/entregas")
    @Operation(summary = "Entregas de la tarea con estado de cada alumno (DOCENTE dueño, ADMIN)")
    public List<EntregaAlumno> entregas(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return entregaService.deLaTarea(id, identidad.usuarioId(jwt), identidad.esAdmin(jwt));
    }

    @PatchMapping("/entregas/{id}/calificar")
    public EntregaResponse calificar(@PathVariable Long id, @Valid @RequestBody CalificarRequest req, @AuthenticationPrincipal Jwt jwt) {
        return calificacionService.calificar(id, req, identidad.usuarioId(jwt), identidad.esAdmin(jwt));
    }

    @PatchMapping("/entregas/{id}/devolver")
    public EntregaResponse devolver(@PathVariable Long id, @Valid @RequestBody DevolverRequest req, @AuthenticationPrincipal Jwt jwt) {
        return calificacionService.devolver(id, req, identidad.usuarioId(jwt), identidad.esAdmin(jwt));
    }

    @PatchMapping("/{id}/calificaciones")
    @Operation(summary = "Calificación masiva de entregas de una tarea (DOCENTE)")
    public CalificacionMasivaResultado calificarMasivo(@PathVariable Long id, @Valid @RequestBody CalificacionMasivaRequest req, @AuthenticationPrincipal Jwt jwt) {
        return calificacionService.calificarMasivo(id, req, identidad.usuarioId(jwt), identidad.esAdmin(jwt));
    }

    @GetMapping("/entregas/{id}/archivos/{archivoId}/url")
    public DescargaResponse descargarArchivo(@PathVariable Long id, @PathVariable Long archivoId, @AuthenticationPrincipal Jwt jwt) {
        boolean docente = identidad.esDocente(jwt);
        Set<Long> propios = docente || identidad.esAdmin(jwt) ? Set.of() : identidad.alumnosDelUsuario(jwt);
        return entregaService.urlArchivo(id, archivoId, identidad.usuarioId(jwt), identidad.esAdmin(jwt), propios, docente);
    }

    // ---------------------------------------------------------------- alumno / apoderado

    @GetMapping("/mias/pendientes")
    public List<TareaPendienteResponse> pendientes(@AuthenticationPrincipal Jwt jwt) {
        return pendientesService.pendientes(identidad.alumnosDelUsuario(jwt));
    }

    @GetMapping("/mias")
    public List<TareaPendienteResponse> mias(@AuthenticationPrincipal Jwt jwt) {
        return pendientesService.todas(identidad.alumnosDelUsuario(jwt));
    }

    // ---------------------------------------------------------------- utilidades

    private Long alumnoPropio(Jwt jwt) {
        Set<Long> ids = identidad.alumnosDelUsuario(jwt);
        if (ids.size() != 1) {
            throw new AccessDeniedException("Solo un alumno puede realizar entregas con su propia cuenta.");
        }
        return ids.iterator().next();
    }

    /** Participante: personal (docente/secretaría/admin) o alumno/apoderado de la sección de la tarea. */
    private void exigirParticipante(Tarea t, Jwt jwt) {
        if (identidad.esStaff(jwt)) return;
        boolean participa = entregaService.deSeccion(t.getSeccionId(), identidad.alumnosDelUsuario(jwt));
        if (!participa) throw new AccessDeniedException("No participa en esta tarea.");
    }
}
