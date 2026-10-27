package pe.edu.eduspace.planificacion.controller;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.edu.eduspace.planificacion.dto.EsHabilResponse;
import pe.edu.eduspace.planificacion.dto.FeriadoRequest;
import pe.edu.eduspace.planificacion.dto.FeriadoResponse;
import pe.edu.eduspace.planificacion.service.FeriadoService;
import pe.edu.eduspace.planificacion.util.Respuestas;

@RestController
@RequestMapping("/api/v1/planificacion/feriados")
@RequiredArgsConstructor
public class FeriadoController {

    private final FeriadoService service;

    @GetMapping
    public List<FeriadoResponse> listar(@RequestParam(required = false) Long anioId) {
        return service.listar(anioId);
    }

    @GetMapping("/es-habil")
    public EsHabilResponse esHabil(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return service.esHabil(fecha);
    }

    @PostMapping
    public ResponseEntity<FeriadoResponse> crear(@Valid @RequestBody FeriadoRequest request) {
        FeriadoResponse creado = service.crear(request);
        return Respuestas.creado(creado.id(), creado);
    }

    @PutMapping("/{id}")
    public FeriadoResponse actualizar(@PathVariable Long id, @Valid @RequestBody FeriadoRequest request) {
        return service.actualizar(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
