package pe.edu.upeu.eduspace.matricula.controller;

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
import pe.edu.upeu.eduspace.matricula.dto.Dtos.*;
import pe.edu.upeu.eduspace.matricula.dto.PageResponse;
import pe.edu.upeu.eduspace.matricula.security.IdentidadService;
import pe.edu.upeu.eduspace.matricula.service.CupoService;
import pe.edu.upeu.eduspace.matricula.service.MatriculaEnLineaService;
import pe.edu.upeu.eduspace.matricula.service.MatriculaService;
import pe.edu.upeu.eduspace.matricula.service.RatificacionService;
import pe.edu.upeu.eduspace.matricula.service.RequisitoService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/matriculas")
@RequiredArgsConstructor
@Tag(name = "Matrículas", description = "Matrícula, cupos, traslados, retiros y ratificación")
public class MatriculaController {

    private final MatriculaService service;
    private final CupoService cupoService;
    private final RequisitoService requisitoService;
    private final RatificacionService ratificacionService;
    private final IdentidadService identidad;
    private final MatriculaEnLineaService enLinea;

    @GetMapping
    @Operation(summary = "Listar matrículas con filtros (ADMIN, SECRETARIA, DOCENTE)")
    public PageResponse<MatriculaResponse> listar(@RequestParam(required = false) Long anioId,
                                                  @RequestParam(required = false) Long gradoId,
                                                  @RequestParam(required = false) Long seccionId,
                                                  @RequestParam(required = false) String estado,
                                                  @RequestParam(required = false) String texto,
                                                  Pageable pageable) {
        return service.listar(anioId, gradoId, seccionId, estado, texto, pageable);
    }

    @GetMapping("/mias")
    @Operation(summary = "Matrículas de los hijos del apoderado (o del propio alumno)")
    public List<MatriculaResponse> mias(@AuthenticationPrincipal Jwt jwt) {
        return service.delosAlumnos(identidad.alumnosDelUsuario(jwt));
    }

    @GetMapping("/cupos")
    @Operation(summary = "Vacantes disponibles por sección")
    public List<CupoResponse> cupos(@RequestParam Long anioId, @RequestParam(required = false) Long gradoId) {
        return cupoService.vacantes(anioId, gradoId);
    }

    @GetMapping("/seccion/{seccionId}/alumnos")
    @Operation(summary = "Alumnos de una sección")
    public List<AlumnoSeccionResponse> alumnosDeSeccion(@PathVariable Long seccionId) {
        return service.alumnosDeSeccion(seccionId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalle con historial y requisitos (staff o dueño)")
    public MatriculaDetalleResponse detalle(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        MatriculaDetalleResponse d = service.detalle(id);
        identidad.exigirAcceso(jwt, d.matricula().alumnoId());
        return d;
    }

    @PostMapping
    @Operation(summary = "Crear matrícula en PRE_MATRICULA (ADMIN, SECRETARIA)")
    public ResponseEntity<MatriculaResponse> crear(@Valid @RequestBody MatriculaCreateRequest req, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crear(req, identidad.usuarioId(jwt)));
    }

    @PatchMapping("/{id}/traslado")
    @Operation(summary = "Trasladar de sección (mismo grado y año)")
    public MatriculaResponse trasladar(@PathVariable Long id, @Valid @RequestBody TrasladoRequest req, @AuthenticationPrincipal Jwt jwt) {
        return service.trasladar(id, req, identidad.usuarioId(jwt));
    }

    @PatchMapping("/{id}/retiro")
    @Operation(summary = "Retirar una matrícula MATRICULADO")
    public MatriculaResponse retirar(@PathVariable Long id, @Valid @RequestBody RetiroRequest req, @AuthenticationPrincipal Jwt jwt) {
        return service.retirar(id, req, identidad.usuarioId(jwt));
    }

    @PatchMapping("/{id}/anular")
    @Operation(summary = "Anular una PRE_MATRICULA")
    public MatriculaResponse anular(@PathVariable Long id, @RequestBody(required = false) AnulacionRequest req, @AuthenticationPrincipal Jwt jwt) {
        return service.anular(id, req == null ? null : req.motivo(), identidad.usuarioId(jwt));
    }

    @PatchMapping("/{id}/aprobar")
    @Operation(summary = "Aprobar una matrícula pagada (solo ADMIN): pasa a MATRICULADO y, si fue en línea, envía el acceso por correo")
    public MatriculaResponse aprobar(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        MatriculaResponse r = service.aprobar(id, identidad.usuarioId(jwt));
        enLinea.aprobada(id);
        return r;
    }

    @PatchMapping("/{id}/rechazar")
    @Operation(summary = "Rechazar una matrícula pagada (solo ADMIN): se anula, libera el cupo y avisa por correo")
    public MatriculaResponse rechazar(@PathVariable Long id, @Valid @RequestBody AnulacionRequest req, @AuthenticationPrincipal Jwt jwt) {
        MatriculaResponse r = service.rechazar(id, req.motivo(), identidad.usuarioId(jwt));
        enLinea.rechazada(id, req.motivo());
        return r;
    }

    @PostMapping("/{id}/reenviar-acceso")
    @Operation(summary = "Reenviar el correo de acceso de una matrícula en línea ya aprobada (solo ADMIN)")
    public void reenviarAcceso(@PathVariable Long id) {
        enLinea.reenviarAcceso(id);
    }

    @PutMapping("/{id}/requisitos/{tipo}")
    @Operation(summary = "Marcar un requisito de la matrícula")
    public RequisitoResponse requisito(@PathVariable Long id, @PathVariable String tipo,
                                       @Valid @RequestBody RequisitoRequest req, @AuthenticationPrincipal Jwt jwt) {
        return requisitoService.marcar(id, tipo, req, identidad.usuarioId(jwt));
    }

    @PostMapping("/ratificacion")
    @Operation(summary = "Ratificación individual o masiva para el año siguiente")
    public RatificacionResultado ratificar(@Valid @RequestBody RatificacionRequest req, @AuthenticationPrincipal Jwt jwt) {
        return ratificacionService.ratificar(req, identidad.usuarioId(jwt));
    }
}
