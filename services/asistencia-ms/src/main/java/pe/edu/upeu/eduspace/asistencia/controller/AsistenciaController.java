package pe.edu.upeu.eduspace.asistencia.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import pe.edu.upeu.eduspace.asistencia.dto.Dtos.*;
import pe.edu.upeu.eduspace.asistencia.dto.PageResponse;
import pe.edu.upeu.eduspace.asistencia.security.IdentidadService;
import pe.edu.upeu.eduspace.asistencia.service.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/asistencia")
@RequiredArgsConstructor
@Tag(name = "Asistencia", description = "Sesiones, registro masivo, correcciones, justificaciones y reportes")
public class AsistenciaController {

    private final SesionService sesionService;
    private final RegistroAsistenciaService registroService;
    private final CierreSesionService cierreService;
    private final CorreccionService correccionService;
    private final JustificacionService justificacionService;
    private final ReporteService reporteService;
    private final IdentidadService identidad;

    @PostMapping("/sesiones")
    @Operation(summary = "Abrir sesión de asistencia (DOCENTE asignado, ADMIN); idempotente")
    public ResponseEntity<SesionResponse> abrir(@Valid @RequestBody SesionCreateRequest req, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(sesionService.abrir(req, identidad.usuarioId(jwt), identidad.esAdmin(jwt)));
    }

    @GetMapping("/sesiones")
    @Operation(summary = "Hojas de asistencia de una sección en una fecha")
    public List<SesionResponse> sesiones(@RequestParam Long seccionId,
                                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return sesionService.buscar(seccionId, fecha);
    }

    @GetMapping("/sesiones/{id}")
    public SesionResponse sesion(@PathVariable Long id) {
        return sesionService.hoja(id);
    }

    @PutMapping("/sesiones/{id}/registro")
    @Operation(summary = "Guardar la asistencia de toda la sección en una sola petición")
    public SesionResponse registrar(@PathVariable Long id, @Valid @RequestBody RegistroAsistenciaRequest req, @AuthenticationPrincipal Jwt jwt) {
        return registroService.registrar(id, req, identidad.usuarioId(jwt), identidad.esAdmin(jwt));
    }

    @PatchMapping("/sesiones/{id}/cerrar")
    public SesionResponse cerrar(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return cierreService.cerrar(id, identidad.usuarioId(jwt), identidad.esAdmin(jwt));
    }

    @PatchMapping("/detalle/{id}")
    @Operation(summary = "Corregir una asistencia con motivo (ADMIN, SECRETARIA)")
    public DetalleResponse corregir(@PathVariable Long id, @Valid @RequestBody CorreccionRequest req, @AuthenticationPrincipal Jwt jwt) {
        return correccionService.corregir(id, req, identidad.usuarioId(jwt));
    }

    @GetMapping("/alumnos/{alumnoId}/resumen")
    @Operation(summary = "Resumen de asistencia del alumno (dueño o personal)")
    public ResumenAlumnoResponse resumen(@PathVariable Long alumnoId,
                                         @RequestParam(required = false) Long periodoId,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                         @AuthenticationPrincipal Jwt jwt) {
        identidad.exigirAcceso(jwt, alumnoId);
        return reporteService.resumenAlumno(alumnoId, periodoId, desde, hasta);
    }

    @GetMapping("/secciones/{id}/reporte")
    public ReporteSeccionResponse reporte(@PathVariable Long id,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return reporteService.reporteSeccion(id, desde, hasta);
    }

    @GetMapping("/mias")
    @Operation(summary = "Asistencia de los hijos (APODERADO) o propia (ALUMNO)")
    public List<AsistenciaHijoResponse> mias(@AuthenticationPrincipal Jwt jwt) {
        return reporteService.deAlumnos(identidad.alumnosDelUsuario(jwt));
    }

    // ---------------------------------------------------------------- justificaciones

    @PostMapping("/justificaciones")
    @Operation(summary = "Solicitar justificación (APODERADO)")
    public ResponseEntity<JustificacionResponse> solicitar(@Valid @RequestBody JustificacionRequest req, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.status(HttpStatus.CREATED).body(justificacionService.solicitar(req, jwt));
    }

    @GetMapping("/justificaciones")
    @Operation(summary = "Bandeja de justificaciones (SECRETARIA, ADMIN)")
    public PageResponse<JustificacionResponse> bandeja(@RequestParam(required = false) String estado, Pageable pageable) {
        return justificacionService.bandeja(estado, pageable);
    }

    @GetMapping("/justificaciones/mias")
    public List<JustificacionResponse> misJustificaciones(@AuthenticationPrincipal Jwt jwt) {
        return justificacionService.delosAlumnos(identidad.alumnosDelUsuario(jwt));
    }

    @PatchMapping("/justificaciones/{id}/resolver")
    @Operation(summary = "Aprobar o rechazar (SECRETARIA, ADMIN)")
    public JustificacionResponse resolver(@PathVariable Long id, @Valid @RequestBody ResolverJustificacionRequest req, @AuthenticationPrincipal Jwt jwt) {
        return justificacionService.resolver(id, req, identidad.usuarioId(jwt));
    }

    @PostMapping("/justificaciones/{id}/archivo-url")
    @Operation(summary = "URL firmada de subida del sustento (APODERADO)")
    public ArchivoUrlResponse archivoUrl(@PathVariable Long id, @Valid @RequestBody ArchivoUrlRequest req, @AuthenticationPrincipal Jwt jwt) {
        return justificacionService.urlArchivo(id, req, jwt);
    }

    @GetMapping("/justificaciones/{id}/archivo")
    public Map<String, String> archivo(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return Map.of("urlDescarga", justificacionService.urlDescarga(id, jwt));
    }
}
