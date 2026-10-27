package pe.edu.upeu.eduspace.pago.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import pe.edu.upeu.eduspace.pago.client.AlumnoContactoResponse;
import pe.edu.upeu.eduspace.pago.client.AlumnosClient;
import pe.edu.upeu.eduspace.pago.dto.*;
import pe.edu.upeu.eduspace.pago.entity.Comprobante;
import pe.edu.upeu.eduspace.pago.entity.Cuota;
import pe.edu.upeu.eduspace.pago.entity.MatriculaLocal;
import pe.edu.upeu.eduspace.pago.exception.ResourceNotFoundException;
import pe.edu.upeu.eduspace.pago.repository.ComprobanteRepository;
import pe.edu.upeu.eduspace.pago.repository.CuotaRepository;
import pe.edu.upeu.eduspace.pago.repository.MatriculaLocalRepository;
import pe.edu.upeu.eduspace.pago.service.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1/pagos")
@RequiredArgsConstructor
@Tag(name = "Pagos y Cuotas", description = "Operaciones de cobro, estado de cuenta, cuotas y comprobantes")
public class PagoController {

    private final PagoService pagoService;
    private final PagoOnlineService pagoOnlineService;
    private final AnulacionService anulacionService;
    private final ComprobanteService comprobanteService;
    private final ComprobanteRepository comprobanteRepository;
    private final CuotaRepository cuotaRepository;
    private final MatriculaLocalRepository matriculaLocalRepository;
    private final pe.edu.upeu.eduspace.pago.client.AlumnosGateway alumnosGateway;

    @PostMapping
    @Operation(summary = "Registrar cobro en ventanilla o presencial (CAJERO, ADMIN)")
    public ResponseEntity<PagoResponse> registrarPago(@Valid @RequestBody RegistrarPagoRequest req,
                                                     @AuthenticationPrincipal Jwt jwt) {
        Long usuarioId = pe.edu.upeu.eduspace.pago.security.UsuarioActual.id(jwt);
        return ResponseEntity.status(HttpStatus.CREATED).body(pagoService.registrarPago(req, usuarioId));
    }

    @PostMapping("/online")
    @Operation(summary = "Pago en línea simulado desde la app (APODERADO)")
    public ResponseEntity<PagoResponse> pagarOnline(@Valid @RequestBody RegistrarPagoRequest req,
                                                   @AuthenticationPrincipal Jwt jwt) {
        Long usuarioId = pe.edu.upeu.eduspace.pago.security.UsuarioActual.id(jwt);
        return ResponseEntity.status(HttpStatus.CREATED).body(pagoOnlineService.pagarOnlineSimulado(req, usuarioId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener detalle de pago por ID")
    public ResponseEntity<PagoResponse> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(pagoService.obtenerPorId(id));
    }

    @GetMapping("/{id}/comprobante")
    @Operation(summary = "Obtener comprobante emitido por pago ID")
    public ResponseEntity<ComprobanteResponse> obtenerComprobante(@PathVariable Long id) {
        Comprobante comp = comprobanteRepository.findByPagoId(id)
            .orElseThrow(() -> new ResourceNotFoundException("Comprobante no encontrado para el pago ID: " + id));
        return ResponseEntity.ok(comprobanteService.toResponse(comp));
    }

    @PatchMapping("/{id}/anular")
    @Operation(summary = "Anular un pago registrado (ADMIN)")
    public ResponseEntity<Void> anularPago(@PathVariable Long id,
                                          @Valid @RequestBody AnularPagoRequest req,
                                          @AuthenticationPrincipal Jwt jwt) {
        Long usuarioId = pe.edu.upeu.eduspace.pago.security.UsuarioActual.id(jwt);
        anulacionService.anularPago(id, req, usuarioId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/alumnos/{alumnoId}/estado-cuenta")
    @Operation(summary = "Consultar estado de cuenta completo del alumno con todas sus cuotas")
    @Transactional(readOnly = true)
    public ResponseEntity<EstadoCuentaResponse> estadoCuenta(@PathVariable Long alumnoId) {
        List<Cuota> cuotas = cuotaRepository.findByAlumnoIdOrderByFechaVencimientoAsc(alumnoId);
        List<MatriculaLocal> mats = matriculaLocalRepository.findByAlumnoId(alumnoId);

        String alumnoNombre = mats.isEmpty() ? "Alumno " + alumnoId : mats.get(0).getAlumnoNombre();
        String respNombre = mats.isEmpty() ? null : mats.get(0).getResponsableNombre();

        BigDecimal cobrado = BigDecimal.ZERO;
        BigDecimal pendiente = BigDecimal.ZERO;
        BigDecimal vencido = BigDecimal.ZERO;
        LocalDate hoy = LocalDate.now();

        List<CuotaResponse> cuotasResp = new ArrayList<>();
        for (Cuota c : cuotas) {
            long diasAtraso = c.getFechaVencimiento().isBefore(hoy) ? ChronoUnit.DAYS.between(c.getFechaVencimiento(), hoy) : 0;
            if ("PAGADA".equals(c.getEstado())) {
                cobrado = cobrado.add(c.getMontoTotal());
            } else if ("VENCIDA".equals(c.getEstado())) {
                vencido = vencido.add(c.getSaldo());
            } else if ("PENDIENTE".equals(c.getEstado()) || "PARCIAL".equals(c.getEstado())) {
                pendiente = pendiente.add(c.getSaldo());
            }

            cuotasResp.add(CuotaResponse.builder()
                .id(c.getId())
                .matriculaId(c.getMatricula() != null ? c.getMatricula().getMatriculaId() : null)
                .alumnoId(c.getAlumnoId())
                .anioId(c.getAnioId())
                .conceptoId(c.getConcepto().getId())
                .conceptoNombre(c.getConcepto().getNombre())
                .conceptoTipo(c.getConcepto().getTipo())
                .descripcion(c.getDescripcion())
                .numeroCuota(c.getNumeroCuota())
                .montoBase(c.getMontoBase())
                .descuento(c.getDescuento())
                .mora(c.getMora())
                .montoTotal(c.getMontoTotal())
                .saldo(c.getSaldo())
                .fechaVencimiento(c.getFechaVencimiento())
                .estado(c.getEstado())
                .diasAtraso(diasAtraso)
                .avisoEnviado(c.getAvisoEnviado())
                .build());
        }

        return ResponseEntity.ok(EstadoCuentaResponse.builder()
            .alumnoId(alumnoId)
            .alumnoNombre(alumnoNombre)
            .responsableNombre(respNombre)
            .totalCobrado(cobrado)
            .totalPendiente(pendiente)
            .totalVencido(vencido)
            .cuotas(cuotasResp)
            .build());
    }

    @GetMapping("/cuotas")
    @Operation(summary = "Consulta paginada de cuotas por estado, sección o vencimiento")
    @Transactional(readOnly = true)
    public ResponseEntity<PageResponse<CuotaResponse>> listarCuotas(
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) Long alumnoId,
            @RequestParam(required = false) Long seccionId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate vencimiento,
            Pageable pageable) {

        LocalDate hoy = LocalDate.now();
        var page = cuotaRepository.findCuotasFiltradas(estado, alumnoId, seccionId, vencimiento, pageable)
            .map(c -> CuotaResponse.builder()
                .id(c.getId())
                .matriculaId(c.getMatricula() != null ? c.getMatricula().getMatriculaId() : null)
                .alumnoId(c.getAlumnoId())
                .anioId(c.getAnioId())
                .conceptoId(c.getConcepto().getId())
                .conceptoNombre(c.getConcepto().getNombre())
                .conceptoTipo(c.getConcepto().getTipo())
                .descripcion(c.getDescripcion())
                .numeroCuota(c.getNumeroCuota())
                .montoBase(c.getMontoBase())
                .descuento(c.getDescuento())
                .mora(c.getMora())
                .montoTotal(c.getMontoTotal())
                .saldo(c.getSaldo())
                .fechaVencimiento(c.getFechaVencimiento())
                .estado(c.getEstado())
                .diasAtraso(c.getFechaVencimiento().isBefore(hoy) ? ChronoUnit.DAYS.between(c.getFechaVencimiento(), hoy) : 0)
                .avisoEnviado(c.getAvisoEnviado())
                .build());

        return ResponseEntity.ok(PageResponse.from(page));
    }

    @GetMapping("/morosos")
    @Operation(summary = "Reporte de morosidad agrupado por sección/grado")
    @Transactional(readOnly = true)
    public ResponseEntity<MorososResponse> morosos(@RequestParam(required = false) Long seccionId,
                                                  @RequestParam(required = false) Long gradoId) {
        List<MatriculaLocal> matriculas = seccionId != null ?
            matriculaLocalRepository.findBySeccionIdAndEstado(seccionId, "MATRICULADO") :
            matriculaLocalRepository.findAll();

        BigDecimal deudaTotal = BigDecimal.ZERO;
        List<MorososResponse.DetalleMoroso> morosos = new ArrayList<>();

        for (MatriculaLocal m : matriculas) {
            List<Cuota> vencidas = cuotaRepository.findDeudasVencidasByAlumnoId(m.getAlumnoId());
            if (!vencidas.isEmpty()) {
                BigDecimal saldoAlumno = vencidas.stream().map(Cuota::getSaldo).reduce(BigDecimal.ZERO, BigDecimal::add);
                deudaTotal = deudaTotal.add(saldoAlumno);
                morosos.add(MorososResponse.DetalleMoroso.builder()
                    .alumnoId(m.getAlumnoId())
                    .alumnoNombre(m.getAlumnoNombre())
                    .matriculaId(m.getMatriculaId())
                    .responsableNombre(m.getResponsableNombre())
                    .saldoTotalVencido(saldoAlumno)
                    .cuotasVencidas(vencidas.size())
                    .build());
            }
        }

        return ResponseEntity.ok(MorososResponse.builder()
            .seccionId(seccionId)
            .gradoId(gradoId)
            .totalAlumnosMorosos((long) morosos.size())
            .totalDeudaAcumulada(deudaTotal)
            .listaMorosos(morosos)
            .build());
    }

    @GetMapping("/reportes/cobranza")
    @Operation(summary = "Reporte de recaudación y cobranza por periodo y medio de pago")
    public ResponseEntity<PageResponse<PagoResponse>> reporteCobranza(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime hasta,
            @RequestParam(required = false) String medio,
            Pageable pageable) {
        return ResponseEntity.ok(pagoService.listarCobranza(desde, hasta, medio, pageable));
    }

    @GetMapping("/mias/cuotas")
    @Operation(summary = "Consultar cuotas de los hijos del apoderado autenticado")
    @Transactional(readOnly = true)
    public ResponseEntity<List<CuotaResponse>> misCuotas(@AuthenticationPrincipal Jwt jwt) {
        Long usuarioId = pe.edu.upeu.eduspace.pago.security.UsuarioActual.id(jwt);
        List<AlumnoContactoResponse.HijoResumen> hijos = alumnosGateway.hijos(usuarioId);

        List<CuotaResponse> resultado = new ArrayList<>();
        LocalDate hoy = LocalDate.now();

        for (AlumnoContactoResponse.HijoResumen hijo : hijos) {
            List<Cuota> cuotas = cuotaRepository.findByAlumnoIdOrderByFechaVencimientoAsc(hijo.getAlumnoId());
            for (Cuota c : cuotas) {
                resultado.add(CuotaResponse.builder()
                    .id(c.getId())
                    .matriculaId(c.getMatricula() != null ? c.getMatricula().getMatriculaId() : null)
                    .alumnoId(c.getAlumnoId())
                    .anioId(c.getAnioId())
                    .conceptoId(c.getConcepto().getId())
                    .conceptoNombre(c.getConcepto().getNombre())
                    .conceptoTipo(c.getConcepto().getTipo())
                    .descripcion(c.getDescripcion())
                    .numeroCuota(c.getNumeroCuota())
                    .montoBase(c.getMontoBase())
                    .descuento(c.getDescuento())
                    .mora(c.getMora())
                    .montoTotal(c.getMontoTotal())
                    .saldo(c.getSaldo())
                    .fechaVencimiento(c.getFechaVencimiento())
                    .estado(c.getEstado())
                    .diasAtraso(c.getFechaVencimiento().isBefore(hoy) ? ChronoUnit.DAYS.between(c.getFechaVencimiento(), hoy) : 0)
                    .avisoEnviado(c.getAvisoEnviado())
                    .build());
            }
        }
        return ResponseEntity.ok(resultado);
    }
}

