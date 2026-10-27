package pe.edu.upeu.eduspace.pago.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.edu.upeu.eduspace.pago.dto.ConceptoRequest;
import pe.edu.upeu.eduspace.pago.dto.ConceptoResponse;
import pe.edu.upeu.eduspace.pago.service.ConceptoService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/pagos/conceptos")
@RequiredArgsConstructor
@Tag(name = "Conceptos de Pago", description = "Gestión de catálogo de conceptos")
public class ConceptoController {

    private final ConceptoService conceptoService;

    @GetMapping
    @Operation(summary = "Listar conceptos de pago activos")
    public ResponseEntity<List<ConceptoResponse>> listar() {
        return ResponseEntity.ok(conceptoService.listarActivos());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener concepto de pago por ID")
    public ResponseEntity<ConceptoResponse> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(conceptoService.obtenerPorId(id));
    }

    @PostMapping
    @Operation(summary = "Crear nuevo concepto de pago (ADMIN)")
    public ResponseEntity<ConceptoResponse> crear(@Valid @RequestBody ConceptoRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(conceptoService.crear(req));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar concepto de pago (ADMIN)")
    public ResponseEntity<ConceptoResponse> actualizar(@PathVariable Long id, @Valid @RequestBody ConceptoRequest req) {
        return ResponseEntity.ok(conceptoService.actualizar(id, req));
    }
}

