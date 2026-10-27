package pe.edu.eduspace.planificacion.controller;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import pe.edu.eduspace.planificacion.dto.CatalogoUpdateRequest;
import pe.edu.eduspace.planificacion.dto.GradoResponse;
import pe.edu.eduspace.planificacion.dto.NivelResponse;
import pe.edu.eduspace.planificacion.service.GradoService;
import pe.edu.eduspace.planificacion.service.NivelService;

@RestController
@RequestMapping("/api/v1/planificacion")
@RequiredArgsConstructor
public class NivelGradoController {

    private final NivelService nivelService;
    private final GradoService gradoService;

    @GetMapping("/niveles")
    public List<NivelResponse> niveles() {
        return nivelService.listar();
    }

    @GetMapping("/niveles/{id}")
    public NivelResponse nivel(@PathVariable Long id) {
        return nivelService.obtener(id);
    }

    @PutMapping("/niveles/{id}")
    public NivelResponse actualizarNivel(@PathVariable Long id, @Valid @RequestBody CatalogoUpdateRequest request) {
        return nivelService.actualizar(id, request);
    }

    @GetMapping("/grados")
    public List<GradoResponse> grados(@RequestParam(required = false) Long nivelId) {
        return gradoService.listar(nivelId);
    }

    @GetMapping("/grados/{id}")
    public GradoResponse grado(@PathVariable Long id) {
        return gradoService.obtener(id);
    }

    @PutMapping("/grados/{id}")
    public GradoResponse actualizarGrado(@PathVariable Long id, @Valid @RequestBody CatalogoUpdateRequest request) {
        return gradoService.actualizar(id, request);
    }
}
