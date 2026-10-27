package pe.edu.eduspace.alumnos.controller;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import pe.edu.eduspace.alumnos.dto.*;
import pe.edu.eduspace.alumnos.service.ContactosService;
import pe.edu.eduspace.alumnos.service.ExpedienteService;
import pe.edu.eduspace.alumnos.service.InternalConsultaService;

/**
 * Lecturas livianas para otros servicios (adenda A3). El Gateway NO enruta {@code /internal/**}: solo es
 * alcanzable dentro de la red Docker.
 */
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalController {

    private final InternalConsultaService internal;
    private final ContactosService contactosService;
    private final ExpedienteService expedienteService;

    @GetMapping("/alumnos/{id}")
    public AlumnoBasicoResponse alumno(@PathVariable Long id) {
        return internal.basico(id);
    }

    @GetMapping("/alumnos/{id}/contactos")
    public ContactosNotificacionResponse contactos(@PathVariable Long id) {
        return contactosService.contactos(id);
    }

    @GetMapping("/alumnos/{id}/expediente-completo")
    public ExpedienteCompletoResponse expediente(@PathVariable Long id) {
        return expedienteService.evaluar(id);
    }

    @GetMapping("/alumnos/{id}/responsable-pago")
    public ResponsablePagoResponse responsablePago(@PathVariable Long id) {
        return internal.responsablePago(id);
    }

    @GetMapping("/alumnos/usuario/{usuarioId}")
    public AlumnoUsuarioResponse alumnoDeUsuario(@PathVariable Long usuarioId) {
        return internal.alumnoDeUsuario(usuarioId);
    }

    @GetMapping("/alumnos/lote")
    public List<AlumnoBasicoResponse> lote(@RequestParam List<Long> ids) {
        return internal.lote(ids);
    }

    @GetMapping("/apoderados/usuario/{usuarioId}/alumnos")
    public List<HijoResponse> hijosDeUsuario(@PathVariable Long usuarioId) {
        return internal.hijosDeUsuario(usuarioId);
    }
}
