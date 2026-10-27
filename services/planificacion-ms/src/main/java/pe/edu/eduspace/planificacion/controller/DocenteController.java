package pe.edu.eduspace.planificacion.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import pe.edu.eduspace.planificacion.dto.DocenteRequest;
import pe.edu.eduspace.planificacion.dto.DocenteResponse;
import pe.edu.eduspace.planificacion.dto.InternalDocenteResponse;
import pe.edu.eduspace.planificacion.dto.PageResponse;
import pe.edu.eduspace.planificacion.dto.VincularUsuarioRequest;
import pe.edu.eduspace.planificacion.service.DocenteService;
import pe.edu.eduspace.planificacion.service.InternalConsultaService;
import pe.edu.eduspace.planificacion.util.Respuestas;

@RestController
@RequestMapping("/api/v1/planificacion/docentes")
@RequiredArgsConstructor
public class DocenteController {

    private final DocenteService service;
    private final InternalConsultaService internal;

    /** El docente de la sesión (por el claim usuarioId del JWT): el token no trae docenteId. 404 si el usuario no está vinculado. */
    @GetMapping("/mi")
    public InternalDocenteResponse mi(@AuthenticationPrincipal Jwt jwt) {
        return internal.docentePorUsuario(jwt.getClaim("usuarioId"));
    }

    @GetMapping
    public PageResponse<DocenteResponse> buscar(@RequestParam(required = false) String q,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        int tamano = Math.min(Math.max(size, 1), 100);
        return service.buscar(q, PageRequest.of(Math.max(page, 0), tamano, Sort.by("apellidos", "nombres")));
    }

    @GetMapping("/{id}")
    public DocenteResponse obtener(@PathVariable Long id) {
        return service.obtener(id);
    }

    @PostMapping
    public ResponseEntity<DocenteResponse> crear(@Valid @RequestBody DocenteRequest request) {
        DocenteResponse creado = service.crear(request);
        return Respuestas.creado(creado.id(), creado);
    }

    @PutMapping("/{id}")
    public DocenteResponse actualizar(@PathVariable Long id, @Valid @RequestBody DocenteRequest request) {
        return service.actualizar(id, request);
    }

    @PatchMapping("/{id}/usuario")
    public DocenteResponse vincularUsuario(@PathVariable Long id, @Valid @RequestBody VincularUsuarioRequest request) {
        return service.vincularUsuario(id, request.usuarioId());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
