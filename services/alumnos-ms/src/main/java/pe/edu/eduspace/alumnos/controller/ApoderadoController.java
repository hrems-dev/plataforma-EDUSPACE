package pe.edu.eduspace.alumnos.controller;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pe.edu.eduspace.alumnos.dto.*;
import pe.edu.eduspace.alumnos.service.AccesoService;
import pe.edu.eduspace.alumnos.service.ApoderadoService;

@RestController
@RequestMapping("/api/v1/apoderados")
@RequiredArgsConstructor
public class ApoderadoController {

    private final ApoderadoService service;
    private final AccesoService acceso;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SECRETARIA')")
    public PageResponse<ApoderadoResponse> buscar(@RequestParam(required = false) String q,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        int tamano = Math.min(Math.max(size, 1), 100);
        return service.buscar(q, PageRequest.of(Math.max(page, 0), tamano, Sort.by("apellidos", "nombres")));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SECRETARIA','APODERADO')")
    public ApoderadoResponse obtener(@PathVariable Long id) {
        acceso.verificarLecturaApoderado(id);
        return service.obtener(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SECRETARIA')")
    public ApoderadoResponse actualizar(@PathVariable Long id, @Valid @RequestBody ApoderadoRequest request) {
        return service.actualizar(id, request);
    }

    @PatchMapping("/{id}/usuario")
    @PreAuthorize("hasAnyRole('ADMIN','SECRETARIA')")
    public ApoderadoResponse vincularUsuario(@PathVariable Long id, @Valid @RequestBody VincularUsuarioRequest request) {
        return service.vincularUsuario(id, request.usuarioId());
    }

    @GetMapping("/{id}/hijos")
    @PreAuthorize("hasAnyRole('ADMIN','SECRETARIA','APODERADO')")
    public List<HijoResponse> hijos(@PathVariable Long id) {
        acceso.verificarLecturaApoderado(id);
        return service.hijos(id);
    }
}
