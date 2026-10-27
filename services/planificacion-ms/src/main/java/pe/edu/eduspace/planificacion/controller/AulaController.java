package pe.edu.eduspace.planificacion.controller;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.edu.eduspace.planificacion.dto.AulaRequest;
import pe.edu.eduspace.planificacion.dto.AulaResponse;
import pe.edu.eduspace.planificacion.entity.TipoAula;
import pe.edu.eduspace.planificacion.service.AulaService;
import pe.edu.eduspace.planificacion.util.Respuestas;

@RestController
@RequestMapping("/api/v1/planificacion/aulas")
@RequiredArgsConstructor
public class AulaController {

    private final AulaService service;

    @GetMapping
    public List<AulaResponse> listar(@RequestParam(required = false) TipoAula tipo) {
        return service.listar(tipo);
    }

    @GetMapping("/{id}")
    public AulaResponse obtener(@PathVariable Long id) {
        return service.obtener(id);
    }

    @PostMapping
    public ResponseEntity<AulaResponse> crear(@Valid @RequestBody AulaRequest request) {
        AulaResponse creada = service.crear(request);
        return Respuestas.creado(creada.id(), creada);
    }

    @PutMapping("/{id}")
    public AulaResponse actualizar(@PathVariable Long id, @Valid @RequestBody AulaRequest request) {
        return service.actualizar(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
