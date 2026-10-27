package pe.edu.eduspace.planificacion.controller;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.edu.eduspace.planificacion.dto.CambioEstadoRequest;
import pe.edu.eduspace.planificacion.dto.PeriodoRequest;
import pe.edu.eduspace.planificacion.dto.PeriodoResponse;
import pe.edu.eduspace.planificacion.service.PeriodoService;
import pe.edu.eduspace.planificacion.util.Respuestas;

@RestController
@RequestMapping("/api/v1/planificacion/periodos")
@RequiredArgsConstructor
public class PeriodoController {

    private final PeriodoService service;

    @GetMapping
    public List<PeriodoResponse> listar(@RequestParam(required = false) Long anioId) {
        return service.listar(anioId);
    }

    @GetMapping("/{id}")
    public PeriodoResponse obtener(@PathVariable Long id) {
        return service.obtener(id);
    }

    @PostMapping
    public ResponseEntity<PeriodoResponse> crear(@Valid @RequestBody PeriodoRequest request) {
        PeriodoResponse creado = service.crear(request);
        return Respuestas.creado(creado.id(), creado);
    }

    @PutMapping("/{id}")
    public PeriodoResponse actualizar(@PathVariable Long id, @Valid @RequestBody PeriodoRequest request) {
        return service.actualizar(id, request);
    }

    @PatchMapping("/{id}/estado")
    public PeriodoResponse cambiarEstado(@PathVariable Long id, @Valid @RequestBody CambioEstadoRequest request) {
        return service.cambiarEstado(id, request.estado());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
