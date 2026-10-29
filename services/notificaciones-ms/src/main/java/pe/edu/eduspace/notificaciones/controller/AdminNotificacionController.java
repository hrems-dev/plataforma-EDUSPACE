package pe.edu.eduspace.notificaciones.controller;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import pe.edu.eduspace.notificaciones.dto.EnvioManualRequest;
import pe.edu.eduspace.notificaciones.dto.EnvioManualResponse;
import pe.edu.eduspace.notificaciones.dto.NotificacionAdminResponse;
import pe.edu.eduspace.notificaciones.dto.NotificacionEditarRequest;
import pe.edu.eduspace.notificaciones.dto.PageResponse;
import pe.edu.eduspace.notificaciones.dto.PlantillaCrearRequest;
import pe.edu.eduspace.notificaciones.dto.PlantillaRequest;
import pe.edu.eduspace.notificaciones.entity.Canal;
import pe.edu.eduspace.notificaciones.entity.EstadoNotificacion;
import pe.edu.eduspace.notificaciones.service.NotificacionAdminService;
import pe.edu.eduspace.notificaciones.dto.PlantillaResponse;
import pe.edu.eduspace.notificaciones.service.EnvioManualService;
import pe.edu.eduspace.notificaciones.service.PlantillaService;

/** Plantillas (alta, edición, baja), envío manual y administración de lo ya enviado: solo ADMIN (la regla está en SecurityConfig, antes de leer el cuerpo). */
@RestController
@RequestMapping("/api/v1/notificaciones")
@RequiredArgsConstructor
public class AdminNotificacionController {

    private final PlantillaService plantillas;
    private final EnvioManualService envioManual;
    private final NotificacionAdminService administracion;

    @GetMapping("/plantillas")
    public List<PlantillaResponse> plantillas() {
        return plantillas.listar();
    }

    @PostMapping("/plantillas")
    public ResponseEntity<PlantillaResponse> crearPlantilla(@Valid @RequestBody PlantillaCrearRequest request) {
        return ResponseEntity.status(201).body(plantillas.crear(request));
    }

    @DeleteMapping("/plantillas/{id}")
    public ResponseEntity<Void> eliminarPlantilla(@PathVariable Long id) {
        plantillas.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/plantillas/{id}")
    public PlantillaResponse actualizarPlantilla(@PathVariable Long id, @Valid @RequestBody PlantillaRequest request) {
        return plantillas.actualizar(id, request);
    }

    @PostMapping("/enviar")
    public EnvioManualResponse enviar(@Valid @RequestBody EnvioManualRequest request) {
        return envioManual.enviar(request);
    }

    @GetMapping("/admin")
    public PageResponse<NotificacionAdminResponse> listar(@RequestParam(required = false) String tipo,
                                                          @RequestParam(required = false) Canal canal,
                                                          @RequestParam(required = false) EstadoNotificacion estado,
                                                          @RequestParam(required = false) Long usuarioId,
                                                          @RequestParam(required = false) String texto,
                                                          @RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "20") int size) {
        return administracion.listar(tipo, canal, estado, usuarioId, texto,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)));
    }

    @PutMapping("/admin/{id}")
    public NotificacionAdminResponse editar(@PathVariable Long id, @Valid @RequestBody NotificacionEditarRequest request) {
        return administracion.editar(id, request);
    }

    @DeleteMapping("/admin/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        administracion.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
