package pe.edu.upeu.eduspace.pago.service;

import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upeu.eduspace.pago.config.KafkaConfig;
import pe.edu.upeu.eduspace.pago.dto.AnularPagoRequest;
import pe.edu.upeu.eduspace.pago.entity.*;
import pe.edu.upeu.eduspace.pago.event.EventoSobre;
import pe.edu.upeu.eduspace.pago.event.PagoAnuladoEvent;
import pe.edu.upeu.eduspace.pago.exception.BusinessRuleException;
import pe.edu.upeu.eduspace.pago.exception.ResourceNotFoundException;
import pe.edu.upeu.eduspace.pago.repository.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnulacionService {

    private final PagoRepository pagoRepository;
    private final CuotaRepository cuotaRepository;
    private final ComprobanteRepository comprobanteRepository;
    private final OutboxEventoRepository outboxEventoRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void anularPago(Long pagoId, AnularPagoRequest req, Long usuarioId) {
        Pago pago = pagoRepository.findById(pagoId)
            .orElseThrow(() -> new ResourceNotFoundException("Pago no encontrado: " + pagoId));

        if ("ANULADO".equals(pago.getEstado())) {
            throw new BusinessRuleException("El pago ya se encuentra anulado.");
        }

        if (pago.getCajaSesion() != null && "CERRADA".equals(pago.getCajaSesion().getEstado())) {
            throw new BusinessRuleException("No se puede anular un pago asociado a una caja ya cerrada sin autorización extraordinaria.");
        }

        // Revertir saldos de cuotas
        for (PagoDetalle det : pago.getDetalles()) {
            Cuota cuota = det.getCuota();
            cuota.setSaldo(cuota.getSaldo().add(det.getMontoAplicado()));
            if (cuota.getSaldo().compareTo(cuota.getMontoTotal()) >= 0) {
                cuota.setEstado("PENDIENTE");
            } else {
                cuota.setEstado("PARCIAL");
            }
            cuota.setActualizadaEn(LocalDateTime.now());
            cuotaRepository.save(cuota);
        }

        // Marcar pago y comprobante anulados
        pago.setEstado("ANULADO");
        pago.setAnuladoEn(LocalDateTime.now());
        pago.setAnuladoPor(usuarioId);
        pago.setMotivoAnulacion(req.getMotivo());
        pagoRepository.save(pago);

        comprobanteRepository.findByPagoId(pagoId).ifPresent(c -> {
            c.setEstado("ANULADO");
            comprobanteRepository.save(c);
        });

        // Registrar en outbox
        try {
            UUID eventId = UUID.randomUUID();
            String traceId = MDC.get("traceId");

            Long matriculaId = null;
            String conceptoTipo = "OTRO";
            for (PagoDetalle det : pago.getDetalles()) {
                Cuota c = det.getCuota();
                if (matriculaId == null && c.getMatricula() != null) matriculaId = c.getMatricula().getMatriculaId();
                String tipo = c.getConcepto().getTipo();
                if ("MATRICULA".equals(tipo)) conceptoTipo = "MATRICULA";
                else if ("PENSION".equals(tipo) && !"MATRICULA".equals(conceptoTipo)) conceptoTipo = "PENSION";
            }
            PagoAnuladoEvent payload = PagoAnuladoEvent.builder()
                .pagoId(pago.getId())
                .alumnoId(pago.getAlumnoId())
                .motivo(req.getMotivo())
                .matriculaId(matriculaId)
                .conceptoTipo(conceptoTipo)
                .monto(pago.getMontoTotal())
                .build();

            EventoSobre<PagoAnuladoEvent> sobre = EventoSobre.<PagoAnuladoEvent>builder()
                .eventId(eventId)
                .eventType(KafkaConfig.TOPIC_PAGO_ANULADO)
                .occurredAt(LocalDateTime.now())
                .version(1)
                .traceId(traceId != null ? traceId : UUID.randomUUID().toString())
                .payload(payload)
                .build();

            OutboxEvento outbox = OutboxEvento.builder()
                .id(eventId)
                .aggregateType("PAGO")
                .aggregateId(String.valueOf(pago.getId()))
                .topic(KafkaConfig.TOPIC_PAGO_ANULADO)
                .clave(String.valueOf(pago.getId()))
                .payload(EventoSobre.escribir(objectMapper, sobre))
                .creadoEn(LocalDateTime.now())
                .intentos((short) 0)
                .build();

            outboxEventoRepository.save(outbox);
            log.info("Pago {} anulado con éxito por usuario {}", pagoId, usuarioId);
        } catch (Exception e) {
            log.error("Error al registrar outbox de pago anulado: {}", e.getMessage(), e);
            throw new RuntimeException("Error en persistencia outbox de anulación", e);
        }
    }
}

