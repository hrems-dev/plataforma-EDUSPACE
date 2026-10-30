package pe.edu.upeu.eduspace.matricula.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import pe.edu.upeu.eduspace.matricula.dto.PublicoDtos.*;
import pe.edu.upeu.eduspace.matricula.service.MatriculaEnLineaService;

/**
 * Matrícula en línea del sitio web del colegio: no exige sesión. Protegida por el límite de peticiones del Gateway, la validación
 * de datos, el campo trampa y un token por trámite (quien lo inicia es quien puede pagar y bajar la constancia).
 */
@RestController
@RequestMapping("/api/v1/publico/matricula")
@RequiredArgsConstructor
@Tag(name = "Matrícula en línea (público)")
public class PublicoMatriculaController {

    static final String TOKEN = "X-Matricula-Token";

    private final MatriculaEnLineaService servicio;

    @GetMapping("/oferta")
    @Operation(summary = "Grados con vacantes y sus costos de matrícula y pensión")
    public Oferta oferta() {
        return servicio.oferta();
    }

    @PostMapping
    @Operation(summary = "Registra al alumno y a su apoderado y reserva el cupo; devuelve el token del trámite")
    public ResponseEntity<Inicio> iniciar(@Valid @RequestBody Solicitud solicitud) {
        return ResponseEntity.status(HttpStatus.CREATED).body(servicio.iniciar(solicitud));
    }

    @PostMapping("/{referencia}/pagar")
    @Operation(summary = "Paga la matrícula con la pasarela simulada, confirma la matrícula, crea las cuentas y envía el correo")
    public Resumen pagar(@PathVariable String referencia, @RequestHeader(TOKEN) String token, @Valid @RequestBody PagoRequest pago) {
        return servicio.pagar(referencia, token, pago.tarjeta());
    }

    @GetMapping("/{referencia}")
    public Resumen resumen(@PathVariable String referencia, @RequestHeader(TOKEN) String token) {
        return servicio.resumen(referencia, token);
    }

    @GetMapping(value = "/{referencia}/constancia", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> constancia(@PathVariable String referencia, @RequestHeader(TOKEN) String token) {
        byte[] pdf = servicio.constancia(referencia, token);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("Constancia-matricula-" + referencia + ".pdf").build().toString())
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .contentType(MediaType.APPLICATION_PDF).body(pdf);
    }
}
