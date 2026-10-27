package pe.edu.eduspace.planificacion.controller;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.edu.eduspace.planificacion.dto.BloqueRequest;
import pe.edu.eduspace.planificacion.dto.BloqueResponse;
import pe.edu.eduspace.planificacion.entity.Turno;
import pe.edu.eduspace.planificacion.service.BloqueHorarioService;
import pe.edu.eduspace.planificacion.util.Respuestas;

@RestController
@RequestMapping("/api/v1/planificacion/bloques")
@RequiredArgsConstructor
public class BloqueHorarioController {

    private final BloqueHorarioService service;

    @GetMapping
    public List<BloqueResponse> listar(@RequestParam(required = false) Turno turno) {
        return service.listar(turno);
    }

    @GetMapping("/{id}")
    public BloqueResponse obtener(@PathVariable Long id) {
        return service.obtener(id);
    }

    @PostMapping
    public ResponseEntity<BloqueResponse> crear(@Valid @RequestBody BloqueRequest request) {
        BloqueResponse creado = service.crear(request);
        return Respuestas.creado(creado.id(), creado);
    }

    @PutMapping("/{id}")
    public BloqueResponse actualizar(@PathVariable Long id, @Valid @RequestBody BloqueRequest request) {
        return service.actualizar(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
