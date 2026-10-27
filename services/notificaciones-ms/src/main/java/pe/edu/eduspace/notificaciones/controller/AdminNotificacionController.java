package pe.edu.eduspace.notificaciones.controller;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import pe.edu.eduspace.notificaciones.dto.EnvioManualRequest;
import pe.edu.eduspace.notificaciones.dto.EnvioManualResponse;
import pe.edu.eduspace.notificaciones.dto.PlantillaRequest;
import pe.edu.eduspace.notificaciones.dto.PlantillaResponse;
import pe.edu.eduspace.notificaciones.service.EnvioManualService;
import pe.edu.eduspace.notificaciones.service.PlantillaService;

/** Plantillas y envío manual: solo ADMIN (la regla está en SecurityConfig, antes de leer el cuerpo). */
@RestController
@RequestMapping("/api/v1/notificaciones")
@RequiredArgsConstructor
public class AdminNotificacionController {

    private final PlantillaService plantillas;
    private final EnvioManualService envioManual;

    @GetMapping("/plantillas")
    public List<PlantillaResponse> plantillas() {
        return plantillas.listar();
    }

    @PutMapping("/plantillas/{id}")
    public PlantillaResponse actualizarPlantilla(@PathVariable Long id, @Valid @RequestBody PlantillaRequest request) {
        return plantillas.actualizar(id, request);
    }

    @PostMapping("/enviar")
    public EnvioManualResponse enviar(@Valid @RequestBody EnvioManualRequest request) {
        return envioManual.enviar(request);
    }
}
