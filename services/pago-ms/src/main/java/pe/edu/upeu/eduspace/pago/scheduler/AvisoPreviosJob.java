package pe.edu.upeu.eduspace.pago.scheduler;

import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upeu.eduspace.pago.config.KafkaConfig;
import pe.edu.upeu.eduspace.pago.config.PagoProperties;
import pe.edu.upeu.eduspace.pago.entity.Cuota;
import pe.edu.upeu.eduspace.pago.entity.OutboxEvento;
import pe.edu.upeu.eduspace.pago.event.EventoSobre;
import pe.edu.upeu.eduspace.pago.event.PagoPorVencerEvent;
import pe.edu.upeu.eduspace.pago.repository.CuotaRepository;
import pe.edu.upeu.eduspace.pago.repository.OutboxEventoRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class AvisoPreviosJob {

    private final CuotaRepository cuotaRepository;
    private final OutboxEventoRepository outboxEventoRepository;
    private final PagoProperties pagoProperties;
    private final ObjectMapper objectMapper;

    @Scheduled(cron = "0 0 6 * * *", zone = "America/Lima")
    @SchedulerLock(name = "pago_avisos_previos_job", lockAtLeastFor = "15s", lockAtMostFor = "10m")
    @Transactional
    public void procesarAvisosPorVencer() {
        ejecutar();
    }

    /** Separado del @Scheduled (con lock de ShedLock) para poder invocarlo directamente. */
    @Transactional
    public void ejecutar() {
        LocalDate hoy = LocalDate.now();
        LocalDate limite = hoy.plusDays(pagoProperties.getDiasAvisoPrevio());
        log.info("Iniciando job de avisos por vencer entre {} y {}", hoy, limite);

        List<Cuota> cuotas = cuotaRepository.findCuotasParaAvisoPrevio(hoy, limite);
        for (Cuota c : cuotas) {
            c.setAvisoEnviado(true);
            cuotaRepository.save(c);

            try {
                UUID eventId = UUID.randomUUID();
                PagoPorVencerEvent payload = PagoPorVencerEvent.builder()
                    .cuotaId(c.getId())
                    .alumnoId(c.getAlumnoId())
                    .matriculaId(c.getMatricula() != null ? c.getMatricula().getMatriculaId() : null)
                    .concepto(c.getDescripcion())
                    .monto(c.getMontoTotal())
                    .saldo(c.getSaldo())
                    .fechaVencimiento(c.getFechaVencimiento())
                    .build();

                EventoSobre<PagoPorVencerEvent> sobre = EventoSobre.<PagoPorVencerEvent>builder()
                    .eventId(eventId)
                    .eventType(KafkaConfig.TOPIC_PAGO_POR_VENCER)
                    .occurredAt(LocalDateTime.now())
                    .version(1)
                    .traceId(UUID.randomUUID().toString())
                    .payload(payload)
                    .build();

                OutboxEvento outbox = OutboxEvento.builder()
                    .id(eventId)
                    .aggregateType("CUOTA")
                    .aggregateId(String.valueOf(c.getId()))
                    .topic(KafkaConfig.TOPIC_PAGO_POR_VENCER)
                    .clave(String.valueOf(c.getId()))
                    .payload(EventoSobre.escribir(objectMapper, sobre))
                    .creadoEn(LocalDateTime.now())
                    .intentos((short) 0)
                    .build();

                outboxEventoRepository.save(outbox);
            } catch (Exception e) {
                log.error("Error al registrar outbox pago.por-vencer para cuota {}: {}", c.getId(), e.getMessage());
            }
        }
        log.info("Notificadas {} cuotas por vencer.", cuotas.size());
    }
}

