package pe.edu.upeu.eduspace.calificaciones.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.upeu.eduspace.calificaciones.dto.CierrePeriodoRequest;
import pe.edu.upeu.eduspace.calificaciones.dto.CierrePeriodoResponse;
import pe.edu.upeu.eduspace.calificaciones.dto.LibretaAlumnoResponse;
import pe.edu.upeu.eduspace.calificaciones.dto.NotaPendienteResponse;
import pe.edu.upeu.eduspace.calificaciones.dto.NotaRequest;
import pe.edu.upeu.eduspace.calificaciones.dto.NotaResponse;
import pe.edu.upeu.eduspace.calificaciones.dto.NotasLoteRequest;
import pe.edu.upeu.eduspace.calificaciones.dto.PromedioResponse;
import pe.edu.upeu.eduspace.calificaciones.dto.ReaperturaRequest;
import pe.edu.upeu.eduspace.calificaciones.dto.RegistroNotasSeccionResponse;
import pe.edu.upeu.eduspace.calificaciones.dto.ResultadoAnualResponse;
import pe.edu.upeu.eduspace.calificaciones.service.CierrePeriodoService;
import pe.edu.upeu.eduspace.calificaciones.service.ConsultaService;
import pe.edu.upeu.eduspace.calificaciones.service.NotaEventoService;
import pe.edu.upeu.eduspace.calificaciones.service.NotaService;
import pe.edu.upeu.eduspace.calificaciones.service.ResultadoAnualService;

import java.util.List;

@Tag(name = "Calificaciones y Notas", description = "Registro de notas, matriz de sección, cierres de periodo, libretas y resultados anuales")
@RestController
@RequestMapping("/api/v1/calificaciones")
@RequiredArgsConstructor
public class CalificacionesController {

    private final pe.edu.upeu.eduspace.calificaciones.security.IdentidadService identidad;

    private final NotaService notaService;
    private final NotaEventoService notaEventoService;
    private final CierrePeriodoService cierrePeriodoService;
    private final ConsultaService consultaService;
    private final ResultadoAnualService resultadoAnualService;

    @Operation(summary = "Obtener matriz de registro auxiliar de la sección (alumnos x criterios)")
    @GetMapping("/registro")
    @PreAuthorize("hasAnyRole('DOCENTE', 'SECRETARIA', 'ADMIN')")
    public ResponseEntity<RegistroNotasSeccionResponse> obtenerRegistro(
            @RequestParam("seccionId") Long seccionId,
            @RequestParam("cursoId") Long cursoId,
            @RequestParam("periodoId") Long periodoId) {
        return ResponseEntity.ok(consultaService.obtenerRegistroSeccion(seccionId, cursoId, periodoId));
    }

    @Operation(summary = "Registrar notas de toda una sección en lote para un criterio")
    @PutMapping("/notas/lote")
    @PreAuthorize("hasAnyRole('DOCENTE', 'ADMIN')")
    public ResponseEntity<List<NotaResponse>> registrarNotasLote(
            @Valid @RequestBody NotasLoteRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        Long usuarioId = extraerUsuarioId(jwt);
        return ResponseEntity.ok(notaService.registrarNotasLote(request, usuarioId, identidad.roles(jwt).contains("ADMIN")));
    }

    @Operation(summary = "Actualizar una nota individual")
    @PutMapping("/notas/{id}")
    @PreAuthorize("hasAnyRole('DOCENTE', 'ADMIN')")
    public ResponseEntity<NotaResponse> actualizarNota(
            @PathVariable("id") Long id,
            @Valid @RequestBody NotaRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        Long usuarioId = extraerUsuarioId(jwt);
        return ResponseEntity.ok(notaService.registrarOActualizarNota(id, request, usuarioId, identidad.roles(jwt).contains("ADMIN")));
    }

    @Operation(summary = "Consultar notas recibidas de tareas o exámenes pendientes de asignar criterio")
    @GetMapping("/pendientes-asignar")
    @PreAuthorize("hasAnyRole('DOCENTE', 'ADMIN')")
    public ResponseEntity<List<NotaPendienteResponse>> listarPendientesAsignar(
            @RequestParam(value = "seccionId", required = false) Long seccionId,
            @RequestParam(value = "cursoId", required = false) Long cursoId) {
        return ResponseEntity.ok(notaEventoService.listarPendientes(seccionId, cursoId));
    }

    @Operation(summary = "Cerrar periodo académico para una sección (congela promedios)")
    @PostMapping("/periodos/cerrar")
    @PreAuthorize("hasAnyRole('SECRETARIA', 'ADMIN')")
    public ResponseEntity<CierrePeriodoResponse> cerrarPeriodo(
            @Valid @RequestBody CierrePeriodoRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        Long usuarioId = extraerUsuarioId(jwt);
        return ResponseEntity.ok(cierrePeriodoService.cerrarPeriodo(request, usuarioId));
    }

    @Operation(summary = "Reabrir periodo académico cerrado (exclusivo ADMIN con motivo auditado)")
    @PostMapping("/periodos/reabrir")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CierrePeriodoResponse> reabrirPeriodo(
            @Valid @RequestBody ReaperturaRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        Long usuarioId = extraerUsuarioId(jwt);
        return ResponseEntity.ok(cierrePeriodoService.reabrirPeriodo(request, usuarioId));
    }

    @Operation(summary = "Consultar promedios de todos los alumnos de una sección en un periodo")
    @GetMapping("/promedios")
    @PreAuthorize("hasAnyRole('DOCENTE', 'SECRETARIA', 'ADMIN')")
    public ResponseEntity<List<PromedioResponse>> obtenerPromediosSeccion(
            @RequestParam("seccionId") Long seccionId,
            @RequestParam("periodoId") Long periodoId) {
        return ResponseEntity.ok(consultaService.obtenerPromediosSeccion(seccionId, periodoId));
    }

    @Operation(summary = "Consultar libreta oficial de calificaciones de un alumno")
    @GetMapping("/alumnos/{alumnoId}/libreta")
    @PreAuthorize("hasAnyRole('ALUMNO', 'APODERADO', 'DOCENTE', 'SECRETARIA', 'ADMIN')")
    public ResponseEntity<LibretaAlumnoResponse> obtenerLibretaAlumno(
            @PathVariable("alumnoId") Long alumnoId,
            @RequestParam(value = "anioId", required = false) Long anioId,
            @AuthenticationPrincipal Jwt jwt) {
        identidad.exigirAcceso(jwt, alumnoId);
        boolean esAlumnoOApoderado = !identidad.esStaff(jwt);
        return ResponseEntity.ok(consultaService.obtenerLibretaAlumno(alumnoId, anioId, esAlumnoOApoderado));
    }

    @Operation(summary = "Consultar resultados anuales de promoción por sección")
    @GetMapping("/resultados-anuales")
    @PreAuthorize("hasAnyRole('SECRETARIA', 'ADMIN')")
    public ResponseEntity<List<ResultadoAnualResponse>> obtenerResultadosAnuales(
            @RequestParam("seccionId") Long seccionId) {
        return ResponseEntity.ok(resultadoAnualService.calcularResultadosSeccion(seccionId));
    }

    @Operation(summary = "Consultar libretas de los hijos del apoderado autenticado")
    @GetMapping("/mias")
    @PreAuthorize("hasAnyRole('APODERADO', 'ALUMNO')")
    public ResponseEntity<List<LibretaAlumnoResponse>> obtenerLibretasMias(
            @RequestParam(value = "anioId", required = false) Long anioId,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(consultaService.obtenerLibretasDeAlumnos(identidad.alumnosDelUsuario(jwt), anioId));
    }

    private Long extraerUsuarioId(Jwt jwt) {
        if (jwt == null) return null;
        Object val = jwt.getClaim("usuarioId");
        if (val instanceof Number n) return n.longValue();
        if (val instanceof String s && !s.isBlank()) {
            try { return Long.parseLong(s); } catch (NumberFormatException ignored) {}
        }
        try {
            return Long.parseLong(jwt.getSubject());
        } catch (Exception ignored) {
            return null;
        }
    }
}
