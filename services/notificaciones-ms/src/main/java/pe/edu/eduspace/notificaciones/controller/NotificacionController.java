package pe.edu.eduspace.notificaciones.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import pe.edu.eduspace.notificaciones.dto.*;
import pe.edu.eduspace.notificaciones.service.DispositivoService;
import pe.edu.eduspace.notificaciones.service.NotificacionService;
import pe.edu.eduspace.notificaciones.service.PreferenciaService;
import pe.edu.eduspace.notificaciones.util.UsuarioActual;

/** Bandeja, preferencias y dispositivos del usuario autenticado: siempre actúa sobre sus propios datos. */
@RestController
@RequestMapping("/api/v1/notificaciones")
@RequiredArgsConstructor
public class NotificacionController {

    private final NotificacionService service;
    private final PreferenciaService preferencias;
    private final DispositivoService dispositivos;

    @GetMapping("/mias")
    public PageResponse<NotificacionResponse> bandeja(@RequestParam(required = false) Boolean leida,
                                                      @RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        int tamano = Math.min(Math.max(size, 1), 100);
        return service.bandeja(yo(), leida, PageRequest.of(Math.max(page, 0), tamano));
    }

    @GetMapping("/mias/no-leidas/conteo")
    public ConteoNoLeidasResponse conteo() {
        return service.conteoNoLeidas(yo());
    }

    @PatchMapping("/mias/leer-todas")
    public Map<String, Integer> leerTodas() {
        return Map.of("marcadas", service.marcarTodasLeidas(yo()));
    }

    @PatchMapping("/{id}/leida")
    public NotificacionResponse marcarLeida(@PathVariable Long id) {
        return service.marcarLeida(yo(), id);
    }

    @GetMapping("/preferencias")
    public List<PreferenciaResponse> preferencias() {
        return preferencias.listar(yo());
    }

    @PutMapping("/preferencias")
    public List<PreferenciaResponse> cambiarPreferencias(@RequestBody @Valid List<@Valid PreferenciaRequest> cambios) {
        return preferencias.guardar(yo(), cambios);
    }

    @PostMapping("/dispositivos")
    public ResponseEntity<DispositivoResponse> registrar(@Valid @RequestBody DispositivoRequest request) {
        return ResponseEntity.status(201).body(dispositivos.registrar(yo(), request));
    }

    @DeleteMapping("/dispositivos/{token}")
    public ResponseEntity<Void> darDeBaja(@PathVariable String token) {
        dispositivos.desactivar(yo(), token);
        return ResponseEntity.noContent().build();
    }

    private Long yo() {
        Long id = UsuarioActual.id();
        if (id == null) {
            throw new AccessDeniedException("El token no trae el claim usuarioId");
        }
        return id;
    }
}
