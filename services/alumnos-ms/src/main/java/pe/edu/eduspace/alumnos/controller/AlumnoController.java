package pe.edu.eduspace.alumnos.controller;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pe.edu.eduspace.alumnos.dto.*;
import pe.edu.eduspace.alumnos.entity.EstadoAlumno;
import pe.edu.eduspace.alumnos.service.AccesoService;
import pe.edu.eduspace.alumnos.service.AlumnoService;
import pe.edu.eduspace.alumnos.service.EstadoAlumnoService;
import pe.edu.eduspace.alumnos.service.ImportacionAlumnosService;
import pe.edu.eduspace.alumnos.util.Respuestas;
import pe.edu.eduspace.alumnos.util.UsuarioActual;

@RestController
@RequestMapping("/api/v1/alumnos")
@RequiredArgsConstructor
public class AlumnoController {

    private final AlumnoService service;
    private final EstadoAlumnoService estadoService;
    private final AccesoService acceso;
    private final ImportacionAlumnosService importacion;

    /** El CAJERO también busca: necesita ubicar al alumno para cobrar. Solo recibe el resumen (nombre, código y documento). */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SECRETARIA','DOCENTE','CAJERO')")
    public PageResponse<AlumnoResumenResponse> buscar(@RequestParam(required = false) String q,
                                                      @RequestParam(required = false) EstadoAlumno estado,
                                                      @RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        int tamano = Math.min(Math.max(size, 1), 100);
        return service.buscar(q, estado, PageRequest.of(Math.max(page, 0), tamano,
                Sort.by("apellidoPaterno", "apellidoMaterno", "nombres")));
    }

    /** Alumnos del usuario autenticado: para la app del apoderado (sus hijos) y del alumno (él mismo). */
    @GetMapping("/mios")
    @PreAuthorize("hasAnyRole('APODERADO','ALUMNO')")
    public List<HijoResponse> mios() {
        return service.mios(UsuarioActual.id(), UsuarioActual.tieneAlgunRol("APODERADO"));
    }

    @GetMapping("/{id}")
    public AlumnoResponse obtener(@PathVariable Long id) {
        acceso.verificarLecturaAlumno(id);
        return service.obtener(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SECRETARIA')")
    public ResponseEntity<AlumnoResponse> crear(@Valid @RequestBody AlumnoCreateRequest request) {
        AlumnoResponse creado = service.crear(request);
        return Respuestas.creado(creado.id(), creado);
    }

    /** Alta masiva desde CSV (UTF-8): un alumno con su apoderado por fila; el resultado detalla cada fila. */
    @PostMapping(value = "/importacion", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','SECRETARIA')")
    public ImportacionResponse importar(@RequestParam("archivo") org.springframework.web.multipart.MultipartFile archivo)
            throws java.io.IOException {
        return importacion.importar(archivo.getInputStream());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SECRETARIA')")
    public AlumnoResponse actualizar(@PathVariable Long id, @Valid @RequestBody AlumnoUpdateRequest request) {
        return service.actualizar(id, request);
    }

    @PatchMapping("/{id}/usuario")
    @PreAuthorize("hasAnyRole('ADMIN','SECRETARIA')")
    public AlumnoResponse vincularUsuario(@PathVariable Long id, @Valid @RequestBody VincularUsuarioRequest request) {
        return service.vincularUsuario(id, request.usuarioId());
    }

    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasAnyRole('ADMIN','SECRETARIA')")
    public AlumnoResumenResponse cambiarEstado(@PathVariable Long id, @Valid @RequestBody CambioEstadoRequest request) {
        return estadoService.cambiar(id, request, UsuarioActual.id());
    }

    @GetMapping("/{id}/estado/historial")
    @PreAuthorize("hasAnyRole('ADMIN','SECRETARIA')")
    public List<HistorialResponse> historial(@PathVariable Long id) {
        return estadoService.historial(id);
    }
}
