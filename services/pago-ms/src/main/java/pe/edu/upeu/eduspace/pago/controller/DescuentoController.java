package pe.edu.upeu.eduspace.pago.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import pe.edu.upeu.eduspace.pago.dto.AsignarDescuentoRequest;
import pe.edu.upeu.eduspace.pago.dto.DescuentoRequest;
import pe.edu.upeu.eduspace.pago.dto.DescuentoResponse;
import pe.edu.upeu.eduspace.pago.service.DescuentoService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/pagos/descuentos")
@RequiredArgsConstructor
@Tag(name = "Descuentos", description = "Catálogo y asignación de descuentos escolares")
public class DescuentoController {

    private final DescuentoService descuentoService;

    @GetMapping
    @Operation(summary = "Listar catálogo de descuentos activos")
    public ResponseEntity<List<DescuentoResponse>> listar() {
        return ResponseEntity.ok(descuentoService.listarActivos());
    }

    @PostMapping
    @Operation(summary = "Crear nuevo descuento (ADMIN)")
    public ResponseEntity<DescuentoResponse> crear(@Valid @RequestBody DescuentoRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(descuentoService.crear(req));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Editar un descuento del catálogo; no altera cuotas ya generadas (ADMIN)")
    public ResponseEntity<DescuentoResponse> actualizar(@PathVariable Long id, @Valid @RequestBody DescuentoRequest req) {
        return ResponseEntity.ok(descuentoService.actualizar(id, req));
    }

    @PostMapping("/asignar")
    @Operation(summary = "Asignar descuento a un alumno/año lectivo (ADMIN)")
    public ResponseEntity<Void> asignar(@Valid @RequestBody AsignarDescuentoRequest req,
                                        @AuthenticationPrincipal Jwt jwt) {
        Long usuarioId = pe.edu.upeu.eduspace.pago.security.UsuarioActual.id(jwt);
        descuentoService.asignarDescuentoAlumno(req, usuarioId);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}

