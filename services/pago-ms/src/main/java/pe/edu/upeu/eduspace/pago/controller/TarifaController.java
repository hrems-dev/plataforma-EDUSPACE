package pe.edu.upeu.eduspace.pago.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.edu.upeu.eduspace.pago.dto.TarifaRequest;
import pe.edu.upeu.eduspace.pago.dto.TarifaResponse;
import pe.edu.upeu.eduspace.pago.service.TarifaService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/pagos/tarifas")
@RequiredArgsConstructor
@Tag(name = "Tarifas", description = "Gestión de tarifas por nivel, grado y año")
public class TarifaController {

    private final TarifaService tarifaService;

    @GetMapping
    @Operation(summary = "Listar tarifas activas por año")
    public ResponseEntity<List<TarifaResponse>> listarPorAnio(@RequestParam Long anioId) {
        return ResponseEntity.ok(tarifaService.listarPorAnio(anioId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener tarifa por ID")
    public ResponseEntity<TarifaResponse> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(tarifaService.obtenerPorId(id));
    }

    @PostMapping
    @Operation(summary = "Crear nueva tarifa (ADMIN)")
    public ResponseEntity<TarifaResponse> crear(@Valid @RequestBody TarifaRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tarifaService.crear(req));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar/desactivar tarifa (ADMIN)")
    public ResponseEntity<TarifaResponse> actualizar(@PathVariable Long id, @Valid @RequestBody TarifaRequest req) {
        return ResponseEntity.ok(tarifaService.actualizar(id, req));
    }
}

