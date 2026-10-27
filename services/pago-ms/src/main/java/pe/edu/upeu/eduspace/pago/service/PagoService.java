package pe.edu.upeu.eduspace.pago.service;

import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upeu.eduspace.pago.config.KafkaConfig;
import pe.edu.upeu.eduspace.pago.dto.*;
import pe.edu.upeu.eduspace.pago.entity.*;
import pe.edu.upeu.eduspace.pago.event.EventoSobre;
import pe.edu.upeu.eduspace.pago.event.PagoConfirmadoEvent;
import pe.edu.upeu.eduspace.pago.exception.BusinessRuleException;
import pe.edu.upeu.eduspace.pago.exception.ResourceNotFoundException;
import pe.edu.upeu.eduspace.pago.repository.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PagoService {

    private final PagoRepository pagoRepository;
    private final PagoDetalleRepository pagoDetalleRepository;
    private final CuotaRepository cuotaRepository;
    private final CajaSesionRepository cajaSesionRepository;
    private final OutboxEventoRepository outboxEventoRepository;
    private final ComprobanteService comprobanteService;
    private final ObjectMapper objectMapper;

    @Transactional
    public PagoResponse registrarPago(RegistrarPagoRequest req, Long usuarioId) {
        // 1. Idempotencia: si ya existe la clave, retornar el pago original sin duplicar cobro
        Optional<Pago> pagoExistente = pagoRepository.findByIdempotencyKey(req.getIdempotencyKey());
        if (pagoExistente.isPresent()) {
            log.info("Pago idempotente detectado con clave: {}. Retornando registro existente.", req.getIdempotencyKey());
            return toResponse(pagoExistente.get());
        }

        // 2. Validar caja si es pago presencial
        CajaSesion cajaSesion = null;
        if (!"ONLINE_SIMULADO".equals(req.getMedio())) {
            cajaSesion = cajaSesionRepository.findFirstByCajeroUsuarioIdAndEstado(usuarioId, "ABIERTA")
                .orElseThrow(() -> new BusinessRuleException("Debe tener una caja ABIERTA para registrar cobros presenciales."));
        }

        // 3. Validar montos y cuotas
        BigDecimal sumaAplicada = BigDecimal.ZERO;
        List<Cuota> cuotasAfectadas = new ArrayList<>();
        List<PagoDetalle> detalles = new ArrayList<>();

        Long matriculaIdPrincipal = null;
        String conceptoTipoPrincipal = "OTRO";
        String conceptoNombrePrincipal = "Cobro educativo";

        for (PagoDetalleRequest detReq : req.getDetalles()) {
            Cuota cuota = cuotaRepository.findById(detReq.getCuotaId())
                .orElseThrow(() -> new ResourceNotFoundException("Cuota no encontrada: " + detReq.getCuotaId()));

            if (!"PENDIENTE".equals(cuota.getEstado()) && !"PARCIAL".equals(cuota.getEstado()) && !"VENCIDA".equals(cuota.getEstado())) {
                throw new BusinessRuleException(String.format("La cuota %d está en estado %s y no admite cobros.", cuota.getId(), cuota.getEstado()));
            }

            if (detReq.getMontoAplicado().compareTo(cuota.getSaldo()) > 0) {
                throw new BusinessRuleException(String.format("El monto a pagar (S/ %.2f) supera el saldo de la cuota %d (S/ %.2f).",
                    detReq.getMontoAplicado(), cuota.getId(), cuota.getSaldo()));
            }

            sumaAplicada = sumaAplicada.add(detReq.getMontoAplicado());
            cuotasAfectadas.add(cuota);

            if (matriculaIdPrincipal == null && cuota.getMatricula() != null) {
                matriculaIdPrincipal = cuota.getMatricula().getMatriculaId();
            }
            if ("MATRICULA".equals(cuota.getConcepto().getTipo())) {
                conceptoTipoPrincipal = "MATRICULA";
                conceptoNombrePrincipal = "MATRICULA";
            } else if ("PENSION".equals(cuota.getConcepto().getTipo()) && !"MATRICULA".equals(conceptoTipoPrincipal)) {
                conceptoTipoPrincipal = "PENSION";
                conceptoNombrePrincipal = "PENSION";
            }
        }

        if (sumaAplicada.compareTo(req.getMontoTotal()) != 0) {
            throw new BusinessRuleException(String.format("La suma de los montos aplicados (S/ %.2f) no coincide con el total del pago (S/ %.2f).",
                sumaAplicada, req.getMontoTotal()));
        }

        // 4. Crear cabecera de Pago
        Pago pago = Pago.builder()
            .alumnoId(req.getAlumnoId())
            .pagadorNombre(req.getPagadorNombre())
            .pagadorDocumento(req.getPagadorDocumento())
            .montoTotal(req.getMontoTotal())
            .medio(req.getMedio())
            .referencia(req.getReferencia())
            .estado("CONFIRMADO")
            .cajaSesion(cajaSesion)
            .registradoPor(usuarioId)
            .pagadoEn(LocalDateTime.now())
            .idempotencyKey(req.getIdempotencyKey())
            .build();

        Pago pagoGuardado = pagoRepository.save(pago);

        // 5. Aplicar montos a cada cuota y guardar detalles (con bloqueo optimista @Version)
        List<Long> cuotaIds = new ArrayList<>();
        for (int i = 0; i < req.getDetalles().size(); i++) {
            PagoDetalleRequest detReq = req.getDetalles().get(i);
            Cuota cuota = cuotasAfectadas.get(i);

            BigDecimal nuevoSaldo = cuota.getSaldo().subtract(detReq.getMontoAplicado());
            cuota.setSaldo(nuevoSaldo);
            if (nuevoSaldo.compareTo(BigDecimal.ZERO) == 0) {
                cuota.setEstado("PAGADA");
            } else {
                cuota.setEstado("PARCIAL");
            }
            cuota.setActualizadaEn(LocalDateTime.now());
            cuotaRepository.save(cuota); // Dispara validación optimista de versión

            PagoDetalle detalle = PagoDetalle.builder()
                .pago(pagoGuardado)
                .cuota(cuota)
                .montoAplicado(detReq.getMontoAplicado())
                .build();
            detalles.add(pagoDetalleRepository.save(detalle));
            cuotaIds.add(cuota.getId());
        }
        pagoGuardado.setDetalles(detalles);

        // 6. Emitir comprobante interno
        Comprobante comprobante = comprobanteService.emitirComprobante(pagoGuardado, req.getTipoComprobante());

        // 7. Escribir evento en OUTBOX transaccional
        crearOutboxPagoConfirmado(pagoGuardado, matriculaIdPrincipal, cuotaIds, conceptoTipoPrincipal, conceptoNombrePrincipal);

        log.info("Pago ID {} registrado exitosamente por monto S/ {}.", pagoGuardado.getId(), pagoGuardado.getMontoTotal());
        return toResponse(pagoGuardado, comprobante);
    }

    private void crearOutboxPagoConfirmado(Pago pago, Long matriculaId, List<Long> cuotaIds, String conceptoTipo, String concepto) {
        try {
            UUID eventId = UUID.randomUUID();
            String traceId = MDC.get("traceId");

            PagoConfirmadoEvent payload = PagoConfirmadoEvent.builder()
                .pagoId(pago.getId())
                .alumnoId(pago.getAlumnoId())
                .matriculaId(matriculaId)
                .cuotaIds(cuotaIds)
                .conceptoTipo(conceptoTipo)
                .monto(pago.getMontoTotal())
                .concepto(concepto)
                .build();

            EventoSobre<PagoConfirmadoEvent> sobre = EventoSobre.<PagoConfirmadoEvent>builder()
                .eventId(eventId)
                .eventType(KafkaConfig.TOPIC_PAGO_CONFIRMADO)
                .occurredAt(LocalDateTime.now())
                .version(1)
                .traceId(traceId != null ? traceId : UUID.randomUUID().toString())
                .payload(payload)
                .build();

            String json = EventoSobre.escribir(objectMapper, sobre);

            OutboxEvento outbox = OutboxEvento.builder()
                .id(eventId)
                .aggregateType("PAGO")
                .aggregateId(String.valueOf(pago.getId()))
                .topic(KafkaConfig.TOPIC_PAGO_CONFIRMADO)
                .clave(String.valueOf(pago.getId()))
                .payload(json)
                .creadoEn(LocalDateTime.now())
                .intentos((short) 0)
                .build();

            outboxEventoRepository.save(outbox);
            log.info("Evento outbox registrado para pago confirmado {}", pago.getId());
        } catch (Exception e) {
            log.error("Error al registrar evento outbox de pago confirmado: {}", e.getMessage(), e);
            throw new RuntimeException("Error en persistencia de outbox transaccional", e);
        }
    }

    @Transactional(readOnly = true)
    public PagoResponse obtenerPorId(Long id) {
        Pago p = pagoRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Pago no encontrado: " + id));
        return toResponse(p);
    }

    @Transactional(readOnly = true)
    public PageResponse<PagoResponse> listarCobranza(LocalDateTime desde, LocalDateTime hasta, String medio, Pageable pageable) {
        Page<Pago> page = pagoRepository.findReporteCobranza(
            desde != null ? desde : LocalDateTime.now().minusMonths(1),
            hasta != null ? hasta : LocalDateTime.now(),
            medio,
            pageable
        );
        return PageResponse.from(page.map(this::toResponse));
    }

    public PagoResponse toResponse(Pago p) {
        // Solo consulta: nunca emite comprobantes (evita consumir correlativos en lecturas)
        Comprobante c = p.getId() != null ? comprobanteService.buscarPorPago(p.getId()).orElse(null) : null;
        return toResponse(p, c);
    }

    public PagoResponse toResponse(Pago p, Comprobante c) {
        List<PagoResponse.PagoDetalleResponse> detResponses = new ArrayList<>();
        if (p.getDetalles() != null) {
            for (PagoDetalle d : p.getDetalles()) {
                detResponses.add(PagoResponse.PagoDetalleResponse.builder()
                    .id(d.getId())
                    .cuotaId(d.getCuota().getId())
                    .cuotaDescripcion(d.getCuota().getDescripcion())
                    .montoAplicado(d.getMontoAplicado())
                    .nuevoSaldoCuota(d.getCuota().getSaldo())
                    .nuevoEstadoCuota(d.getCuota().getEstado())
                    .build());
            }
        }

        ComprobanteResponse compResp = null;
        if (c != null) {
            compResp = comprobanteService.toResponse(c);
        }

        return PagoResponse.builder()
            .id(p.getId())
            .alumnoId(p.getAlumnoId())
            .pagadorNombre(p.getPagadorNombre())
            .pagadorDocumento(p.getPagadorDocumento())
            .montoTotal(p.getMontoTotal())
            .medio(p.getMedio())
            .referencia(p.getReferencia())
            .estado(p.getEstado())
            .cajaSesionId(p.getCajaSesion() != null ? p.getCajaSesion().getId() : null)
            .registradoPor(p.getRegistradoPor())
            .pagadoEn(p.getPagadoEn())
            .idempotencyKey(p.getIdempotencyKey())
            .comprobante(compResp)
            .detalles(detResponses)
            .build();
    }
}

