package pe.edu.eduspace.alumnos.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pe.edu.eduspace.alumnos.dto.SaludRequest;
import pe.edu.eduspace.alumnos.dto.SaludResponse;
import pe.edu.eduspace.alumnos.service.SaludService;
import pe.edu.eduspace.alumnos.util.UsuarioActual;

@RestController
@RequestMapping("/api/v1/alumnos/{alumnoId}/salud")
@RequiredArgsConstructor
public class SaludController {

    private final SaludService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SECRETARIA','DOCENTE')")
    public SaludResponse obtener(@PathVariable Long alumnoId) {
        return service.obtener(alumnoId, UsuarioActual.id());
    }

    @PutMapping
    @PreAuthorize("hasAnyRole('ADMIN','SECRETARIA')")
    public SaludResponse guardar(@PathVariable Long alumnoId, @Valid @RequestBody SaludRequest request) {
        return service.guardar(alumnoId, request, UsuarioActual.id());
    }
}
