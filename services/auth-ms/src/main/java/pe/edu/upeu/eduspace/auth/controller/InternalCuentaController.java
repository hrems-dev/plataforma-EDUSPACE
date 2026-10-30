package pe.edu.upeu.eduspace.auth.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.edu.upeu.eduspace.auth.dto.Dtos.CuentaPersonaRequest;
import pe.edu.upeu.eduspace.auth.dto.Dtos.UsuarioResponse;
import pe.edu.upeu.eduspace.auth.service.UsuarioService;

/**
 * Altas de cuenta que otros servicios piden con el token de servicio (p. ej. matricula-ms al confirmar una matrícula en línea).
 * No pasa por el Gateway. Nunca crea cuentas de ADMIN.
 */
@RestController
@RequestMapping("/internal/cuentas")
@RequiredArgsConstructor
public class InternalCuentaController {

    private final UsuarioService usuarios;

    @PostMapping
    public ResponseEntity<UsuarioResponse> crear(@Valid @RequestBody CuentaPersonaRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(usuarios.crearCuentaPersona(req).usuario());
    }
}
