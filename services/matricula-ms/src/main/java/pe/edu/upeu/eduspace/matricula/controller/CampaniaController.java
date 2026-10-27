package pe.edu.upeu.eduspace.matricula.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.edu.upeu.eduspace.matricula.dto.Dtos.CampaniaRequest;
import pe.edu.upeu.eduspace.matricula.dto.Dtos.CampaniaResponse;
import pe.edu.upeu.eduspace.matricula.service.CampaniaService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/matriculas/campanias")
@RequiredArgsConstructor
@Tag(name = "Campañas de matrícula")
public class CampaniaController {

    private final CampaniaService service;

    @GetMapping
    public List<CampaniaResponse> listar(@RequestParam(required = false) Long anioId) {
        return service.listar(anioId);
    }

    @PostMapping
    @Operation(summary = "Crear campaña (ADMIN)")
    public ResponseEntity<CampaniaResponse> crear(@Valid @RequestBody CampaniaRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crear(req));
    }

    @PutMapping("/{id}")
    public CampaniaResponse actualizar(@PathVariable Long id, @Valid @RequestBody CampaniaRequest req) {
        return service.actualizar(id, req);
    }

    @PatchMapping("/{id}/abrir")
    @Operation(summary = "Abrir campaña (una ABIERTA por año)")
    public CampaniaResponse abrir(@PathVariable Long id) {
        return service.abrir(id);
    }

    @PatchMapping("/{id}/cerrar")
    public CampaniaResponse cerrar(@PathVariable Long id) {
        return service.cerrar(id);
    }
}
