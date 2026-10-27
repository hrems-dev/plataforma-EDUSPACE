package pe.edu.eduspace.planificacion.controller;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.edu.eduspace.planificacion.dto.SeccionRequest;
import pe.edu.eduspace.planificacion.dto.SeccionResponse;
import pe.edu.eduspace.planificacion.service.SeccionService;
import pe.edu.eduspace.planificacion.util.Respuestas;

@RestController
@RequestMapping("/api/v1/planificacion/secciones")
@RequiredArgsConstructor
public class SeccionController {

    private final SeccionService service;

    @GetMapping
    public List<SeccionResponse> listar(@RequestParam(required = false) Long anioId,
                                        @RequestParam(required = false) Long gradoId) {
        return service.listar(anioId, gradoId);
    }

    @GetMapping("/{id}")
    public SeccionResponse obtener(@PathVariable Long id) {
        return service.obtener(id);
    }

    @PostMapping
    public ResponseEntity<SeccionResponse> crear(@Valid @RequestBody SeccionRequest request) {
        SeccionResponse creada = service.crear(request);
        return Respuestas.creado(creada.id(), creada);
    }

    @PutMapping("/{id}")
    public SeccionResponse actualizar(@PathVariable Long id, @Valid @RequestBody SeccionRequest request) {
        return service.actualizar(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
