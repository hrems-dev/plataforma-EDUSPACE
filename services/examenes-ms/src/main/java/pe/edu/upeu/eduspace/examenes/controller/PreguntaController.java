package pe.edu.upeu.eduspace.examenes.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import pe.edu.upeu.eduspace.examenes.dto.PageResponse;
import pe.edu.upeu.eduspace.examenes.dto.PreguntaRequest;
import pe.edu.upeu.eduspace.examenes.dto.PreguntaResponse;
import pe.edu.upeu.eduspace.examenes.service.PreguntaService;

@RestController
@RequestMapping("/api/v1/examenes/preguntas")
@RequiredArgsConstructor
@Tag(name = "Banco de Preguntas", description = "Gestión de preguntas y opciones del docente")
public class PreguntaController {

    private final PreguntaService preguntaService;
    private final pe.edu.upeu.eduspace.examenes.security.IdentidadService identidad;

    @GetMapping
    @Operation(summary = "Consultar banco de preguntas filtrado por curso, tema o tipo")
    public ResponseEntity<PageResponse<PreguntaResponse>> listar(
            @RequestParam Long cursoId,
            @RequestParam(required = false) Long docenteId,
            @RequestParam(required = false) String tema,
            @RequestParam(required = false) String tipo,
            Pageable pageable) {
        return ResponseEntity.ok(preguntaService.listarBanco(cursoId, docenteId, tema, tipo, pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener detalle de pregunta por ID")
    public ResponseEntity<PreguntaResponse> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(preguntaService.obtenerPorId(id));
    }

    @PostMapping
    @Operation(summary = "Crear nueva pregunta en el banco (DOCENTE)")
    public ResponseEntity<PreguntaResponse> crear(@Valid @RequestBody PreguntaRequest req,
                                                  @AuthenticationPrincipal Jwt jwt) {
        Long docenteId = identidad.docenteId(jwt);
        return ResponseEntity.status(HttpStatus.CREATED).body(preguntaService.crear(req, docenteId));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar pregunta del banco (DOCENTE)")
    public ResponseEntity<PreguntaResponse> actualizar(@PathVariable Long id,
                                                       @Valid @RequestBody PreguntaRequest req,
                                                       @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(preguntaService.actualizar(id, req, identidad.docenteId(jwt)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar o desactivar pregunta (DOCENTE)")
    public ResponseEntity<Void> eliminar(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        preguntaService.eliminar(id, identidad.docenteId(jwt));
        return ResponseEntity.noContent().build();
    }
}

