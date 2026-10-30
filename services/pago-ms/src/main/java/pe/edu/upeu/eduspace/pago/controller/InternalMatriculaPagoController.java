package pe.edu.upeu.eduspace.pago.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import pe.edu.upeu.eduspace.pago.dto.PagoDetalleRequest;
import pe.edu.upeu.eduspace.pago.dto.PagoResponse;
import pe.edu.upeu.eduspace.pago.dto.RegistrarPagoRequest;
import pe.edu.upeu.eduspace.pago.entity.Cuota;
import pe.edu.upeu.eduspace.pago.entity.Tarifa;
import pe.edu.upeu.eduspace.pago.exception.BusinessRuleException;
import pe.edu.upeu.eduspace.pago.exception.ResourceNotFoundException;
import pe.edu.upeu.eduspace.pago.repository.ConceptoPagoRepository;
import pe.edu.upeu.eduspace.pago.repository.CuotaRepository;
import pe.edu.upeu.eduspace.pago.repository.TarifaRepository;
import pe.edu.upeu.eduspace.pago.service.PagoService;

/**
 * Lo que matricula-ms necesita de pago-ms para la matrícula en línea del sitio público: cuánto cuesta, qué cuotas tiene una
 * matrícula y cobrar la cuota de matrícula con el pago simulado. Solo responde con el token de servicio (no pasa por el Gateway).
 */
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
@Transactional   // las cuotas cargan su concepto de forma perezosa
@Tag(name = "Internal matrícula en línea")
public class InternalMatriculaPagoController {

    private final TarifaRepository tarifas;
    private final ConceptoPagoRepository conceptos;
    private final CuotaRepository cuotas;
    private final PagoService pagos;

    public record Costo(Long tarifaId, BigDecimal monto, Short numCuotas, Short mesInicio, Short diaVencimiento) {}

    public record OfertaCosto(Costo matricula, Costo pension) {}

    public record CuotaResumen(Long id, String concepto, String descripcion, Short numeroCuota, BigDecimal montoBase, BigDecimal descuento,
                               BigDecimal montoTotal, BigDecimal saldo, LocalDate fechaVencimiento, String estado) {}

    public record CobroMatricula(@NotNull Long matriculaId, @NotNull Long alumnoId, @NotBlank @Size(max = 200) String pagadorNombre,
                                 @Size(max = 20) String pagadorDocumento, @Size(max = 80) String referencia) {}

    public record ResultadoCobro(Long pagoId, String comprobante, BigDecimal monto, Long cuotaId) {}

    /** Matrícula y pensión que corresponden a un grado (la tarifa más específica aplicable). */
    @GetMapping("/tarifas/aplicable")
    public OfertaCosto aplicable(@RequestParam Long anioId, @RequestParam Long nivelId, @RequestParam Long gradoId) {
        return new OfertaCosto(costo("MATRICULA", anioId, nivelId, gradoId), costo("PENSION", anioId, nivelId, gradoId));
    }

    @GetMapping("/pagos/matricula/{matriculaId}/cuotas")
    public List<CuotaResumen> cuotasDe(@PathVariable Long matriculaId) {
        return cuotas.findByMatriculaMatriculaId(matriculaId).stream()
            .sorted(java.util.Comparator.comparing(Cuota::getFechaVencimiento).thenComparing(Cuota::getNumeroCuota))
            .map(c -> new CuotaResumen(c.getId(), c.getConcepto().getTipo(), c.getDescripcion(), c.getNumeroCuota(), c.getMontoBase(),
                c.getDescuento(), c.getMontoTotal(), c.getSaldo(), c.getFechaVencimiento(), c.getEstado()))
            .toList();
    }

    /** Cobra la cuota de matrícula pendiente con el medio ONLINE_SIMULADO. Idempotente por matrícula. */
    @PostMapping("/pagos/matricula-en-linea")
    public ResultadoCobro cobrar(@Valid @RequestBody CobroMatricula req) {
        Cuota cuota = cuotas.findByMatriculaMatriculaId(req.matriculaId()).stream()
            .filter(c -> "MATRICULA".equals(c.getConcepto().getTipo()))
            .findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("La matrícula " + req.matriculaId() + " todavía no tiene cuota de matrícula."));
        if (!req.alumnoId().equals(cuota.getAlumnoId())) {
            throw new BusinessRuleException("La cuota no corresponde a ese alumno.");
        }
        RegistrarPagoRequest r = new RegistrarPagoRequest();
        r.setAlumnoId(req.alumnoId());
        r.setPagadorNombre(req.pagadorNombre());
        r.setPagadorDocumento(req.pagadorDocumento());
        r.setMedio("ONLINE_SIMULADO");
        r.setReferencia(req.referencia() == null || req.referencia().isBlank() ? "WEB-" + System.currentTimeMillis() : req.referencia());
        r.setIdempotencyKey("WEB-MAT-" + req.matriculaId());
        r.setTipoComprobante("RECIBO");
        // Si ya estaba pagada (reintento) el registro idempotente devuelve el pago original.
        BigDecimal monto = cuota.getSaldo().signum() > 0 ? cuota.getSaldo() : cuota.getMontoTotal();
        r.setMontoTotal(monto);
        PagoDetalleRequest d = new PagoDetalleRequest();
        d.setCuotaId(cuota.getId());
        d.setMontoAplicado(monto);
        r.setDetalles(List.of(d));
        PagoResponse p = pagos.registrarPago(r, null);
        return new ResultadoCobro(p.getId(), p.getComprobante() != null ? p.getComprobante().getNumeroCompleto() : null, p.getMontoTotal(), cuota.getId());
    }

    private Costo costo(String codigoConcepto, Long anioId, Long nivelId, Long gradoId) {
        return conceptos.findByCodigo(codigoConcepto)
            .flatMap(c -> tarifas.findTarifaAplicable(anioId, nivelId, gradoId, c.getId()))
            .map((Tarifa t) -> new Costo(t.getId(), t.getMonto(), t.getNumCuotas(), t.getMesInicio(), t.getDiaVencimiento()))
            .orElse(null);
    }
}
