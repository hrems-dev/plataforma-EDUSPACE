package pe.edu.upeu.eduspace.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.edu.upeu.eduspace.auth.dto.Dtos.*;
import pe.edu.upeu.eduspace.auth.service.UsuarioService;

@RestController
@RequestMapping("/api/v1/auth/usuarios")
@RequiredArgsConstructor
@Tag(name = "Usuarios (solo ADMIN)")
public class UsuarioController {

    private final UsuarioService service;

    @GetMapping
    @Operation(summary = "Listar usuarios; filtros: texto, rol, activo")
    public PageResponse<UsuarioResponse> listar(@RequestParam(required = false) String texto,
                                                @RequestParam(required = false) String rol,
                                                @RequestParam(required = false) Boolean activo,
                                                Pageable pageable) {
        return service.listar(texto, rol, activo, pageable);
    }

    @GetMapping("/{id}")
    public UsuarioResponse obtener(@PathVariable Long id) {
        return service.obtener(id);
    }

    @PostMapping
    @Operation(summary = "Crear usuario; sin 'clave' se genera una temporal que se muestra una sola vez")
    public ResponseEntity<UsuarioConClaveTemporal> crear(@Valid @RequestBody UsuarioCreateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crear(req));
    }

    @PostMapping("/cuentas")
    @Operation(summary = "Crea la cuenta de una persona: usuario nombre.apellido y clave inicial = documento (cambio obligatorio al entrar)")
    public ResponseEntity<UsuarioConClaveTemporal> crearCuenta(@Valid @RequestBody CuentaPersonaRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crearCuentaPersona(req));
    }

    @PostMapping("/{id}/restablecer-por-documento")
    @Operation(summary = "Restaura la cuenta: la clave vuelve a ser el documento y debe cambiarse al entrar (no aplica a ADMIN)")
    public UsuarioResponse restablecerPorDocumento(@PathVariable Long id, @Valid @RequestBody RestablecerPorDocumentoRequest req) {
        return service.restablecerPorDocumento(id, req);
    }

    @PutMapping("/{id}")
    public UsuarioResponse actualizar(@PathVariable Long id, @Valid @RequestBody UsuarioUpdateRequest req) {
        return service.actualizar(id, req);
    }

    @PutMapping("/{id}/roles")
    public UsuarioResponse roles(@PathVariable Long id, @Valid @RequestBody RolesRequest req) {
        return service.asignarRoles(id, req);
    }

    @PatchMapping("/{id}/activar")
    public UsuarioResponse activar(@PathVariable Long id) {
        return service.cambiarEstado(id, true);
    }

    @PatchMapping("/{id}/desactivar")
    public UsuarioResponse desactivar(@PathVariable Long id) {
        return service.cambiarEstado(id, false);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "No hay borrado físico (otros servicios guardan usuario_id): desactiva al usuario")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        service.cambiarEstado(id, false);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/desbloquear")
    public UsuarioResponse desbloquear(@PathVariable Long id) {
        return service.desbloquear(id);
    }

    @PostMapping("/{id}/restablecer-clave")
    @Operation(summary = "Genera una clave temporal (se muestra una sola vez) y obliga a cambiarla")
    public UsuarioConClaveTemporal restablecer(@PathVariable Long id) {
        return service.restablecerClave(id);
    }
}
