package pe.edu.eduspace.planificacion.controller;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.edu.eduspace.planificacion.dto.AnioRequest;
import pe.edu.eduspace.planificacion.dto.AnioResponse;
import pe.edu.eduspace.planificacion.service.AnioAcademicoService;
import pe.edu.eduspace.planificacion.util.Respuestas;

@RestController
@RequestMapping("/api/v1/planificacion/anios")
@RequiredArgsConstructor
public class AnioAcademicoController {

    private final AnioAcademicoService service;

    @GetMapping
    public List<AnioResponse> listar() {
        return service.listar();
    }

    @GetMapping("/activo")
    public AnioResponse activo() {
        return service.activo();
    }

    @GetMapping("/{id}")
    public AnioResponse obtener(@PathVariable Long id) {
        return service.obtener(id);
    }

    @PostMapping
    public ResponseEntity<AnioResponse> crear(@Valid @RequestBody AnioRequest request) {
        AnioResponse creado = service.crear(request);
        return Respuestas.creado(creado.id(), creado);
    }

    @PutMapping("/{id}")
    public AnioResponse actualizar(@PathVariable Long id, @Valid @RequestBody AnioRequest request) {
        return service.actualizar(id, request);
    }

    @PatchMapping("/{id}/activar")
    public AnioResponse activar(@PathVariable Long id) {
        return service.activar(id);
    }

    @PatchMapping("/{id}/cerrar")
    public AnioResponse cerrar(@PathVariable Long id) {
        return service.cerrar(id);
    }
}
