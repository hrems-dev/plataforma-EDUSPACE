package pe.edu.upeu.eduspace.calificaciones.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.upeu.eduspace.calificaciones.dto.CriterioRequest;
import pe.edu.upeu.eduspace.calificaciones.dto.CriterioResponse;
import pe.edu.upeu.eduspace.calificaciones.dto.ValidarPesosResponse;
import pe.edu.upeu.eduspace.calificaciones.service.CriterioService;

import java.util.List;

@Tag(name = "Criterios de Evaluación", description = "Gestión de criterios y ponderaciones por periodo, sección y curso")
@RestController
@RequestMapping("/api/v1/calificaciones/criterios")
@RequiredArgsConstructor
public class CriterioController {

    private final CriterioService criterioService;
    private final pe.edu.upeu.eduspace.calificaciones.security.IdentidadService identidad;

    @Operation(summary = "Listar criterios de un curso y periodo")
    @GetMapping
    @PreAuthorize("hasAnyRole('DOCENTE', 'SECRETARIA', 'ADMIN')")
    public ResponseEntity<List<CriterioResponse>> listarCriterios(
            @RequestParam("periodoId") Long periodoId,
            @RequestParam("seccionId") Long seccionId,
            @RequestParam("cursoId") Long cursoId) {
        return ResponseEntity.ok(criterioService.listarPorCursoYPeriodo(periodoId, seccionId, cursoId));
    }

    @Operation(summary = "Crear un nuevo criterio de evaluación")
    @PostMapping
    @PreAuthorize("hasAnyRole('DOCENTE', 'ADMIN')")
    public ResponseEntity<CriterioResponse> crearCriterio(
            @Valid @RequestBody CriterioRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        Long usuarioId = extraerUsuarioId(jwt);
        boolean esAdmin = esAdmin(jwt);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(criterioService.crearCriterio(request, usuarioId, esAdmin));
    }

    @Operation(summary = "Actualizar un criterio existente")
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('DOCENTE', 'ADMIN')")
    public ResponseEntity<CriterioResponse> actualizarCriterio(
            @PathVariable("id") Long id,
            @Valid @RequestBody CriterioRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        Long usuarioId = extraerUsuarioId(jwt);
        boolean esAdmin = esAdmin(jwt);
        return ResponseEntity.ok(criterioService.actualizarCriterio(id, request, usuarioId, esAdmin));
    }

    @Operation(summary = "Eliminar un criterio de evaluación")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('DOCENTE', 'ADMIN')")
    public ResponseEntity<Void> eliminarCriterio(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal Jwt jwt) {
        Long usuarioId = extraerUsuarioId(jwt);
        boolean esAdmin = esAdmin(jwt);
        criterioService.eliminarCriterio(id, usuarioId, esAdmin);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Validar si la suma de pesos de un curso y periodo alcanza el 100%")
    @GetMapping("/validar")
    @PreAuthorize("hasAnyRole('DOCENTE', 'SECRETARIA', 'ADMIN')")
    public ResponseEntity<ValidarPesosResponse> validarPesos(
            @RequestParam("periodoId") Long periodoId,
            @RequestParam("seccionId") Long seccionId,
            @RequestParam("cursoId") Long cursoId) {
        return ResponseEntity.ok(criterioService.validarPesos(periodoId, seccionId, cursoId));
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

    private boolean esAdmin(Jwt jwt) {
        return identidad.roles(jwt).contains("ADMIN");
    }
}
