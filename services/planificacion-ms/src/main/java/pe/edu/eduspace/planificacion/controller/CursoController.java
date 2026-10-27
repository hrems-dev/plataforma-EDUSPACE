package pe.edu.eduspace.planificacion.controller;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.edu.eduspace.planificacion.dto.CursoRequest;
import pe.edu.eduspace.planificacion.dto.CursoResponse;
import pe.edu.eduspace.planificacion.service.CursoService;
import pe.edu.eduspace.planificacion.util.Respuestas;

@RestController
@RequestMapping("/api/v1/planificacion/cursos")
@RequiredArgsConstructor
public class CursoController {

    private final CursoService service;

    @GetMapping
    public List<CursoResponse> listar() {
        return service.listar();
    }

    @GetMapping("/{id}")
    public CursoResponse obtener(@PathVariable Long id) {
        return service.obtener(id);
    }

    @PostMapping
    public ResponseEntity<CursoResponse> crear(@Valid @RequestBody CursoRequest request) {
        CursoResponse creado = service.crear(request);
        return Respuestas.creado(creado.id(), creado);
    }

    @PutMapping("/{id}")
    public CursoResponse actualizar(@PathVariable Long id, @Valid @RequestBody CursoRequest request) {
        return service.actualizar(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
