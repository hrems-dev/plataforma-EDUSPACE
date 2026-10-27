package pe.edu.eduspace.planificacion.controller;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.edu.eduspace.planificacion.dto.AsignacionDocenteRequest;
import pe.edu.eduspace.planificacion.dto.AsignacionRequest;
import pe.edu.eduspace.planificacion.dto.AsignacionResponse;
import pe.edu.eduspace.planificacion.service.AsignacionService;
import pe.edu.eduspace.planificacion.util.Respuestas;

@RestController
@RequestMapping("/api/v1/planificacion/asignaciones")
@RequiredArgsConstructor
public class AsignacionController {

    private final AsignacionService service;

    /** Filtra por sección o por docente (uno de los dos). */
    @GetMapping
    public List<AsignacionResponse> listar(@RequestParam(required = false) Long seccionId,
                                           @RequestParam(required = false) Long docenteId) {
        if (seccionId != null) {
            return service.listarPorSeccion(seccionId);
        }
        if (docenteId != null) {
            return service.listarPorDocente(docenteId);
        }
        throw new IllegalArgumentException("Indique seccionId o docenteId");
    }

    @GetMapping("/{id}")
    public AsignacionResponse obtener(@PathVariable Long id) {
        return service.obtener(id);
    }

    @PostMapping
    public ResponseEntity<AsignacionResponse> crear(@Valid @RequestBody AsignacionRequest request) {
        AsignacionResponse creada = service.crear(request);
        return Respuestas.creado(creada.id(), creada);
    }

    @PatchMapping("/{id}/docente")
    public AsignacionResponse cambiarDocente(@PathVariable Long id, @Valid @RequestBody AsignacionDocenteRequest request) {
        return service.cambiarDocente(id, request.docenteId());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
