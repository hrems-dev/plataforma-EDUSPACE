package pe.edu.eduspace.alumnos.controller;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pe.edu.eduspace.alumnos.dto.VinculoApoderadoRequest;
import pe.edu.eduspace.alumnos.dto.VinculoResponse;
import pe.edu.eduspace.alumnos.dto.VinculoUpdateRequest;
import pe.edu.eduspace.alumnos.service.VinculoService;
import pe.edu.eduspace.alumnos.util.Respuestas;

@RestController
@RequestMapping("/api/v1/alumnos/{alumnoId}/apoderados")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','SECRETARIA')")
public class VinculoController {

    private final VinculoService service;

    @GetMapping
    public List<VinculoResponse> listar(@PathVariable Long alumnoId) {
        return service.listar(alumnoId);
    }

    @PostMapping
    public ResponseEntity<VinculoResponse> agregar(@PathVariable Long alumnoId,
                                                   @Valid @RequestBody VinculoApoderadoRequest request) {
        VinculoResponse creado = service.agregar(alumnoId, request);
        return Respuestas.creado(creado.id(), creado);
    }

    @PutMapping("/{vinculoId}")
    public VinculoResponse actualizar(@PathVariable Long alumnoId, @PathVariable Long vinculoId,
                                      @RequestBody VinculoUpdateRequest request) {
        return service.actualizar(alumnoId, vinculoId, request);
    }

    @DeleteMapping("/{vinculoId}")
    public ResponseEntity<Void> quitar(@PathVariable Long alumnoId, @PathVariable Long vinculoId) {
        service.quitar(alumnoId, vinculoId);
        return ResponseEntity.noContent().build();
    }
}
