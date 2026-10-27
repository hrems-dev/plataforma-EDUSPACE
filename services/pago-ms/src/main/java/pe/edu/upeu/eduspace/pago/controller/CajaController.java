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
import pe.edu.upeu.eduspace.pago.dto.*;
import pe.edu.upeu.eduspace.pago.service.CajaService;

@RestController
@RequestMapping("/api/v1/pagos/caja")
@RequiredArgsConstructor
@Tag(name = "Caja", description = "Apertura, cierre y arqueo de caja por cajero")
public class CajaController {

    private final CajaService cajaService;

    @PostMapping("/abrir")
    @Operation(summary = "Abrir sesión de caja (CAJERO)")
    public ResponseEntity<CajaResponse> abrir(@Valid @RequestBody AbrirCajaRequest req,
                                             @AuthenticationPrincipal Jwt jwt) {
        Long usuarioId = pe.edu.upeu.eduspace.pago.security.UsuarioActual.id(jwt);
        return ResponseEntity.status(HttpStatus.CREATED).body(cajaService.abrirCaja(usuarioId, req));
    }

    @PostMapping("/cerrar")
    @Operation(summary = "Cerrar sesión de caja y calcular diferencia (CAJERO)")
    public ResponseEntity<CierreCajaResponse> cerrar(@Valid @RequestBody CerrarCajaRequest req,
                                                     @AuthenticationPrincipal Jwt jwt) {
        Long usuarioId = pe.edu.upeu.eduspace.pago.security.UsuarioActual.id(jwt);
        return ResponseEntity.ok(cajaService.cerrarCaja(usuarioId, req));
    }

    @GetMapping("/actual")
    @Operation(summary = "Consultar caja abierta del cajero actual (CAJERO)")
    public ResponseEntity<CajaResponse> actual(@AuthenticationPrincipal Jwt jwt) {
        Long usuarioId = pe.edu.upeu.eduspace.pago.security.UsuarioActual.id(jwt);
        return ResponseEntity.ok(cajaService.obtenerCajaActual(usuarioId));
    }

    @GetMapping("/{id}/reporte")
    @Operation(summary = "Reporte y arqueo de una sesión de caja (CAJERO, ADMIN)")
    public ResponseEntity<CierreCajaResponse> reporte(@PathVariable Long id) {
        return ResponseEntity.ok(cajaService.reporteCaja(id));
    }
}

