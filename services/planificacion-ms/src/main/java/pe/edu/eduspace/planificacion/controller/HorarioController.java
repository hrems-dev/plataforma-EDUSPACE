package pe.edu.eduspace.planificacion.controller;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import pe.edu.eduspace.planificacion.dto.*;
import pe.edu.eduspace.planificacion.service.HorarioService;
import pe.edu.eduspace.planificacion.service.PublicacionHorarioService;
import pe.edu.eduspace.planificacion.util.UsuarioActual;

@RestController
@RequestMapping("/api/v1/planificacion/horarios")
@RequiredArgsConstructor
public class HorarioController {

    private final HorarioService service;
    private final PublicacionHorarioService publicacionService;

    @GetMapping("/seccion/{seccionId}")
    public HorarioSeccionResponse porSeccion(@PathVariable Long seccionId) {
        return service.porSeccion(seccionId);
    }

    @GetMapping("/docente/{docenteId}")
    public HorarioDocenteResponse porDocente(@PathVariable Long docenteId) {
        return service.porDocente(docenteId);
    }

    @GetMapping("/validar/{seccionId}")
    public ResultadoValidacionHorario validar(@PathVariable Long seccionId) {
        return service.validar(seccionId);
    }

    @PostMapping
    public ResponseEntity<HorarioResponse> crear(@Valid @RequestBody HorarioRequest request) {
        HorarioResponse creado = service.crear(request);
        URI ubicacion = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(creado.id()).toUri();
        return ResponseEntity.created(ubicacion).body(creado);
    }

    @PutMapping("/{id}")
    public HorarioResponse mover(@PathVariable Long id, @Valid @RequestBody HorarioRequest request) {
        return service.mover(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/publicar/seccion/{seccionId}")
    public ResponseEntity<PublicacionResponse> publicar(@PathVariable Long seccionId) {
        PublicacionResponse publicada = publicacionService.publicar(seccionId, UsuarioActual.id());
        return ResponseEntity.status(201).body(publicada);
    }

    @GetMapping("/publicar/seccion/{seccionId}")
    public List<PublicacionResponse> historial(@PathVariable Long seccionId) {
        return publicacionService.historial(seccionId);
    }
}
