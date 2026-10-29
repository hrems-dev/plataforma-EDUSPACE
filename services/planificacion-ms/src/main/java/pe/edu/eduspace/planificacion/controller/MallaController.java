package pe.edu.eduspace.planificacion.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.edu.eduspace.planificacion.dto.MallaGradoResponse;
import pe.edu.eduspace.planificacion.dto.MallaLoteRequest;
import pe.edu.eduspace.planificacion.dto.MallaLoteResponse;
import pe.edu.eduspace.planificacion.dto.MallaHorasRequest;
import pe.edu.eduspace.planificacion.dto.MallaItemResponse;
import pe.edu.eduspace.planificacion.dto.MallaRequest;
import pe.edu.eduspace.planificacion.service.MallaService;
import pe.edu.eduspace.planificacion.util.Respuestas;

@RestController
@RequestMapping("/api/v1/planificacion/malla")
@RequiredArgsConstructor
public class MallaController {

    private final MallaService service;

    @GetMapping("/grado/{gradoId}")
    public MallaGradoResponse porGrado(@PathVariable Long gradoId) {
        return service.porGrado(gradoId);
    }

    @PostMapping
    public ResponseEntity<MallaItemResponse> agregar(@Valid @RequestBody MallaRequest request) {
        MallaItemResponse creado = service.agregar(request);
        return Respuestas.creado(creado.id(), creado);
    }

    @PostMapping("/lote")
    public MallaLoteResponse agregarLote(@Valid @RequestBody MallaLoteRequest request) {
        return service.agregarLote(request);
    }

    @PutMapping("/{id}")
    public MallaItemResponse actualizarHoras(@PathVariable Long id, @Valid @RequestBody MallaHorasRequest request) {
        return service.actualizarHoras(id, request.horasSemanales());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
