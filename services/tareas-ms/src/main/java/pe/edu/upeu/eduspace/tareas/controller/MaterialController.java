package pe.edu.upeu.eduspace.tareas.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import pe.edu.upeu.eduspace.tareas.dto.Dtos.*;
import pe.edu.upeu.eduspace.tareas.security.IdentidadService;
import pe.edu.upeu.eduspace.tareas.service.MaterialService;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/tareas/materiales")
@RequiredArgsConstructor
@Tag(name = "Materiales", description = "Materiales de curso que suben los docentes (archivos y enlaces)")
public class MaterialController {

    private final MaterialService service;
    private final IdentidadService identidad;

    @PostMapping("/archivo-url")
    @Operation(summary = "URL firmada para subir un archivo de material (DOCENTE asignado al curso)")
    public ArchivoUrlResponse urlSubida(@Valid @RequestBody MaterialArchivoUrlRequest req, @AuthenticationPrincipal Jwt jwt) {
        return service.urlSubida(req, identidad.usuarioId(jwt), identidad.esAdmin(jwt));
    }

    @PostMapping
    @Operation(summary = "Publicar un material (archivo ya subido o enlace) y avisar a la familia")
    public ResponseEntity<MaterialResponse> crear(@Valid @RequestBody MaterialRequest req, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crear(req, identidad.usuarioId(jwt), identidad.esAdmin(jwt)));
    }

    @GetMapping
    @Operation(summary = "Materiales de un curso en una sección (personal, o familia con un alumno en la sección)")
    public List<MaterialResponse> listar(@RequestParam Long seccionId, @RequestParam Long cursoId, @AuthenticationPrincipal Jwt jwt) {
        boolean staff = identidad.esStaff(jwt);
        return service.listar(seccionId, cursoId, staff, staff ? Set.of() : identidad.alumnosDelUsuario(jwt));
    }

    @GetMapping("/{id}/url")
    @Operation(summary = "URL firmada de descarga de un material tipo archivo")
    public DescargaResponse descargar(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        boolean staff = identidad.esStaff(jwt);
        return service.urlDescarga(id, staff, staff ? Set.of() : identidad.alumnosDelUsuario(jwt));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar un material (el docente dueño o ADMIN)")
    public ResponseEntity<Void> eliminar(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        service.eliminar(id, identidad.usuarioId(jwt), identidad.esAdmin(jwt));
        return ResponseEntity.noContent().build();
    }
}
