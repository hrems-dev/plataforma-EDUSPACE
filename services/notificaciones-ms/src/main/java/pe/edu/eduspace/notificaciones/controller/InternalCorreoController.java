package pe.edu.eduspace.notificaciones.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import pe.edu.eduspace.notificaciones.service.CorreoService;

/** Envío de correos que otros servicios solicitan (no pasa por el Gateway; exige el token de servicio). */
@RestController
@RequestMapping("/internal/correos")
@RequiredArgsConstructor
public class InternalCorreoController {

    private final CorreoService correos;

    public record AdjuntoDto(@NotBlank @Size(max = 120) String nombre, @Size(max = 100) String contentType, @NotBlank String base64) {}

    public record CorreoRequest(@NotBlank @Email @Size(max = 150) String para, @NotBlank @Size(max = 200) String asunto,
                                @NotBlank String html, String texto, List<@Valid AdjuntoDto> adjuntos) {}

    public record CorreoResponse(boolean enviado) {}

    @PostMapping
    public CorreoResponse enviar(@Valid @RequestBody CorreoRequest r) {
        List<CorreoService.Adjunto> adj = r.adjuntos() == null ? List.of()
            : r.adjuntos().stream().map(a -> new CorreoService.Adjunto(a.nombre(), a.contentType(), a.base64())).toList();
        correos.enviar(r.para(), r.asunto(), r.html(), r.texto(), adj);
        return new CorreoResponse(true);
    }
}
