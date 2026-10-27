package pe.edu.upeu.eduspace.pago.scheduler;

import tools.jackson.databind.ObjectMapper;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upeu.eduspace.pago.config.KafkaConfig;
import pe.edu.upeu.eduspace.pago.entity.Cuota;
import pe.edu.upeu.eduspace.pago.entity.OutboxEvento;
import pe.edu.upeu.eduspace.pago.event.EventoSobre;
import pe.edu.upeu.eduspace.pago.event.PagoVencidoEvent;
import pe.edu.upeu.eduspace.pago.repository.CuotaRepository;
import pe.edu.upeu.eduspace.pago.repository.OutboxEventoRepository;
import pe.edu.upeu.eduspace.pago.service.MoraService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class VencimientoJob {

    private final CuotaRepository cuotaRepository;
    private final MoraService moraService;
    private final OutboxEventoRepository outboxEventoRepository;
    private final ObjectMapper objectMapper;

    @Scheduled(cron = "0 30 0 * * *", zone = "America/Lima")
    @SchedulerLock(name = "pago_vencimiento_job", lockAtLeastFor = "15s", lockAtMostFor = "10m")
    @Transactional
    public void procesarCuotasVencidas() {
        ejecutar();
    }

    /** Separado del @Scheduled (que toma el lock de ShedLock) para poder invocarlo directamente en pruebas y operaciones. */
    @Transactional
    public void ejecutar() {
        LocalDate hoy = LocalDate.now();
        log.info("Iniciando job diario de cuotas vencidas y mora a fecha: {}", hoy);

        List<Cuota> cuotasVencidas = cuotaRepository.findCuotasPorVencerVencidasSinMarcar(hoy);
        for (Cuota c : cuotasVencidas) {
            c.setEstado("VENCIDA");
            moraService.aplicarMora(c, hoy);
            cuotaRepository.save(c);

            // Publicar evento en outbox pago.vencido
            try {
                UUID eventId = UUID.randomUUID();
                PagoVencidoEvent payload = PagoVencidoEvent.builder()
                    .cuotaId(c.getId())
                    .alumnoId(c.getAlumnoId())
                    .matriculaId(c.getMatricula() != null ? c.getMatricula().getMatriculaId() : null)
                    .concepto(c.getDescripcion())
                    .monto(c.getMontoTotal())
                    .saldo(c.getSaldo())
                    .fechaVencimiento(c.getFechaVencimiento())
                    .build();

                EventoSobre<PagoVencidoEvent> sobre = EventoSobre.<PagoVencidoEvent>builder()
                    .eventId(eventId)
                    .eventType(KafkaConfig.TOPIC_PAGO_VENCIDO)
                    .occurredAt(LocalDateTime.now())
                    .version(1)
                    .traceId(UUID.randomUUID().toString())
                    .payload(payload)
                    .build();

                OutboxEvento outbox = OutboxEvento.builder()
                    .id(eventId)
                    .aggregateType("CUOTA")
                    .aggregateId(String.valueOf(c.getId()))
                    .topic(KafkaConfig.TOPIC_PAGO_VENCIDO)
                    .clave(String.valueOf(c.getId()))
                    .payload(EventoSobre.escribir(objectMapper, sobre))
                    .creadoEn(LocalDateTime.now())
                    .intentos((short) 0)
                    .build();

                outboxEventoRepository.save(outbox);
            } catch (Exception e) {
                log.error("Error al registrar outbox para cuota vencida {}: {}", c.getId(), e.getMessage());
            }
        }
        // La mora sigue corriendo cada día sobre las cuotas que ya estaban VENCIDAS (sin repetir el evento)
        List<Cuota> yaVencidas = cuotaRepository.findByEstado("VENCIDA");
        for (Cuota c : yaVencidas) {
            if (!cuotasVencidas.contains(c)) moraService.aplicarMora(c, hoy);
        }
        log.info("Procesadas {} cuotas recién vencidas y {} mora(s) diarias.", cuotasVencidas.size(), yaVencidas.size());
    }
}

