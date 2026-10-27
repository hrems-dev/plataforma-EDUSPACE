package pe.edu.eduspace.planificacion.controller;

import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import pe.edu.eduspace.planificacion.dto.*;
import pe.edu.eduspace.planificacion.service.AnioAcademicoService;
import pe.edu.eduspace.planificacion.service.CursoService;
import pe.edu.eduspace.planificacion.service.FeriadoService;
import pe.edu.eduspace.planificacion.service.InternalConsultaService;

/**
 * Lecturas livianas para otros servicios (adenda A2). El Gateway NO enruta {@code /internal/**}:
 * solo es alcanzable dentro de la red Docker.
 */
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalController {

    private final InternalConsultaService internal;
    private final AnioAcademicoService anioService;
    private final CursoService cursoService;
    private final FeriadoService feriadoService;

    @GetMapping("/docentes/usuario/{usuarioId}")
    public InternalDocenteResponse docentePorUsuario(@PathVariable Long usuarioId) {
        return internal.docentePorUsuario(usuarioId);
    }

    @GetMapping("/docentes/usuario/{usuarioId}/asignaciones")
    public List<AsignacionInternaResponse> asignaciones(@PathVariable Long usuarioId,
                                                        @RequestParam(required = false) Long anioId) {
        return internal.asignacionesDeUsuario(usuarioId, anioId);
    }

    @GetMapping("/asignaciones/validar")
    public boolean validar(@RequestParam Long docenteId, @RequestParam Long seccionId, @RequestParam Long cursoId) {
        return internal.asignacionValida(docenteId, seccionId, cursoId);
    }

    @GetMapping("/secciones/{id}")
    public SeccionInternaResponse seccion(@PathVariable Long id) {
        return internal.seccion(id);
    }

    @GetMapping("/secciones")
    public List<SeccionInternaResponse> secciones(@RequestParam(required = false) Long anioId,
                                                  @RequestParam(required = false) Long gradoId) {
        return internal.secciones(anioId, gradoId);
    }

    @GetMapping("/anios/activo")
    public AnioResponse anioActivo() {
        return anioService.activo();
    }

    @GetMapping("/anios/{id}")
    public AnioResponse anio(@PathVariable Long id) {
        return anioService.obtener(id);
    }

    @GetMapping("/periodos")
    public List<PeriodoResponse> periodos(@RequestParam Long anioId) {
        return internal.periodos(anioId);
    }

    @GetMapping("/periodos/{id}")
    public PeriodoResponse periodo(@PathVariable Long id) {
        return internal.periodo(id);
    }

    @GetMapping("/cursos/{id}")
    public CursoResponse curso(@PathVariable Long id) {
        return cursoService.obtener(id);
    }

    @GetMapping("/cursos")
    public List<CursoResponse> cursos(@RequestParam List<Long> ids) {
        return cursoService.porIds(ids);
    }

    @GetMapping("/grados/{id}")
    public GradoResponse grado(@PathVariable Long id) {
        return internal.grado(id);
    }

    @GetMapping("/niveles/{id}")
    public NivelResponse nivel(@PathVariable Long id) {
        return internal.nivel(id);
    }

    @GetMapping("/feriados/es-habil")
    public boolean esHabil(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return feriadoService.esHabil(fecha).habil();
    }
}
