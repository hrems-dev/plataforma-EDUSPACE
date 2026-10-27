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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.upeu.eduspace.calificaciones.dto.BoletinResponse;
import pe.edu.upeu.eduspace.calificaciones.dto.GenerarBoletinRequest;
import pe.edu.upeu.eduspace.calificaciones.service.BoletinService;

import java.util.List;
import java.util.Map;

@Tag(name = "Boletines Oficiales", description = "Generación y descarga de boletines académicos oficiales en MinIO")
@RestController
@RequestMapping("/api/v1/calificaciones/boletines")
@RequiredArgsConstructor
public class BoletinController {

    private final BoletinService boletinService;
    private final pe.edu.upeu.eduspace.calificaciones.security.IdentidadService identidad;

    @Operation(summary = "Generar boletines oficiales (por alumno o toda la sección)")
    @PostMapping("/generar")
    @PreAuthorize("hasAnyRole('SECRETARIA', 'ADMIN')")
    public ResponseEntity<List<BoletinResponse>> generarBoletines(
            @Valid @RequestBody GenerarBoletinRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        Long usuarioId = extraerUsuarioId(jwt);
        return ResponseEntity.ok(boletinService.generarBoletines(request, usuarioId));
    }

    @Operation(summary = "Obtener URL prefirmada de descarga de MinIO para un boletín")
    @GetMapping("/{id}/url")
    @PreAuthorize("hasAnyRole('ALUMNO', 'APODERADO', 'DOCENTE', 'SECRETARIA', 'ADMIN')")
    public ResponseEntity<Map<String, String>> obtenerUrlDescarga(@PathVariable("id") Long id,
                                                                  @AuthenticationPrincipal Jwt jwt) {
        identidad.exigirAcceso(jwt, boletinService.obtenerPorId(id).getAlumnoId());
        String url = boletinService.obtenerUrlDescarga(id);
        return ResponseEntity.ok(Map.of("boletinId", String.valueOf(id), "urlDescarga", url));
    }

    @Operation(summary = "Consultar el detalle de un boletín")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ALUMNO', 'APODERADO', 'DOCENTE', 'SECRETARIA', 'ADMIN')")
    public ResponseEntity<BoletinResponse> obtenerPorId(@PathVariable("id") Long id,
                                                        @AuthenticationPrincipal Jwt jwt) {
        BoletinResponse b = boletinService.obtenerPorId(id);
        identidad.exigirAcceso(jwt, b.getAlumnoId());
        return ResponseEntity.ok(b);
    }

    @Operation(summary = "Listar los boletines generados para un alumno")
    @GetMapping
    @PreAuthorize("hasAnyRole('ALUMNO', 'APODERADO', 'DOCENTE', 'SECRETARIA', 'ADMIN')")
    public ResponseEntity<List<BoletinResponse>> listarBoletinesPorAlumno(@RequestParam("alumnoId") Long alumnoId,
                                                                          @AuthenticationPrincipal Jwt jwt) {
        identidad.exigirAcceso(jwt, alumnoId);
        return ResponseEntity.ok(boletinService.listarBoletinesPorAlumno(alumnoId));
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

