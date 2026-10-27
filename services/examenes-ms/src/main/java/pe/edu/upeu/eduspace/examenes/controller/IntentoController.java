package pe.edu.upeu.eduspace.examenes.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import pe.edu.upeu.eduspace.examenes.dto.GuardarRespuestaRequest;
import pe.edu.upeu.eduspace.examenes.dto.IntentoIniciadoResponse;
import pe.edu.upeu.eduspace.examenes.dto.IntentoResultadoResponse;
import pe.edu.upeu.eduspace.examenes.entity.Intento;
import pe.edu.upeu.eduspace.examenes.repository.IntentoRepository;
import pe.edu.upeu.eduspace.examenes.security.IdentidadService;
import pe.edu.upeu.eduspace.examenes.service.IntentoService;
import pe.edu.upeu.eduspace.examenes.service.RespuestaService;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1/examenes")
@RequiredArgsConstructor
@Tag(name = "Intentos de Examen", description = "Rendición de exámenes por estudiantes")
public class IntentoController {

    private final IntentoService intentoService;
    private final RespuestaService respuestaService;
    private final IntentoRepository intentoRepository;
    private final IdentidadService identidad;

    @PostMapping("/{id}/intentos")
    @Operation(summary = "Iniciar o reanudar intento de examen (ALUMNO)")
    public ResponseEntity<IntentoIniciadoResponse> iniciarIntento(@PathVariable Long id,
                                                                 @AuthenticationPrincipal Jwt jwt) {
        Long alumnoId = identidad.alumnoId(jwt);
        return ResponseEntity.status(HttpStatus.CREATED).body(intentoService.iniciarIntento(id, alumnoId));
    }

    @PutMapping("/intentos/{id}/respuestas/{preguntaId}")
    @Operation(summary = "Guardado incremental de respuesta a una pregunta (ALUMNO)")
    public ResponseEntity<Void> guardarRespuesta(@PathVariable Long id,
                                                @PathVariable Long preguntaId,
                                                @RequestBody GuardarRespuestaRequest req,
                                                @AuthenticationPrincipal Jwt jwt) {
        Long alumnoId = identidad.alumnoId(jwt);
        respuestaService.guardarRespuestaIncremental(id, preguntaId, req, alumnoId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/intentos/{id}/enviar")
    @Operation(summary = "Finalizar y enviar examen para calificación (ALUMNO)")
    public ResponseEntity<IntentoResultadoResponse> enviarIntento(@PathVariable Long id,
                                                                 @AuthenticationPrincipal Jwt jwt) {
        Long alumnoId = identidad.alumnoId(jwt);
        return ResponseEntity.ok(intentoService.enviarIntento(id, alumnoId));
    }

    @GetMapping("/intentos/{id}/resultado")
    @Operation(summary = "Consultar resultado y nota de un intento (ALUMNO, APODERADO, DOCENTE)")
    public ResponseEntity<IntentoResultadoResponse> obtenerResultado(@PathVariable Long id,
                                                                     @AuthenticationPrincipal Jwt jwt) {
        Intento intento = intentoRepository.findById(id)
            .orElseThrow(() -> new pe.edu.upeu.eduspace.examenes.exception.ResourceNotFoundException("Intento no encontrado: " + id));
        if (!identidad.roles(jwt).contains("ADMIN") && !identidad.roles(jwt).contains("DOCENTE")
                && !identidad.alumnosVisibles(jwt).contains(intento.getAlumnoId())) {
            throw new org.springframework.security.access.AccessDeniedException("No puede ver el resultado de este intento.");
        }
        return ResponseEntity.ok(intentoService.obtenerResultado(id));
    }

    @GetMapping("/mias")
    @Operation(summary = "Listar exámenes e intentos rendidos por el usuario o sus hijos")
    public ResponseEntity<List<IntentoResultadoResponse>> misIntentos(@AuthenticationPrincipal Jwt jwt) {
        List<IntentoResultadoResponse> respuestas = new ArrayList<>();
        for (Long alumnoId : identidad.alumnosVisibles(jwt)) {
            for (Intento i : intentoRepository.findByAlumnoIdOrderByIniciadoEnDesc(alumnoId)) {
                respuestas.add(intentoService.obtenerResultado(i.getId()));
            }
        }
        return ResponseEntity.ok(respuestas);
    }
}

