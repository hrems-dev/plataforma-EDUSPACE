package pe.edu.upeu.eduspace.pago.messaging;

import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upeu.eduspace.pago.config.KafkaConfig;
import pe.edu.upeu.eduspace.pago.entity.EventoProcesado;
import pe.edu.upeu.eduspace.pago.event.EventoSobre;
import pe.edu.upeu.eduspace.pago.event.MatriculaEvent;
import pe.edu.upeu.eduspace.pago.repository.EventoProcesadoRepository;
import pe.edu.upeu.eduspace.pago.service.GeneradorCuotasService;
import pe.edu.upeu.eduspace.pago.service.MatriculaLocalService;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class MatriculaEventConsumer {

    private final EventoProcesadoRepository eventoProcesadoRepository;
    private final MatriculaLocalService matriculaLocalService;
    private final GeneradorCuotasService generadorCuotasService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = {
        KafkaConfig.TOPIC_MATRICULA_CREADA,
        KafkaConfig.TOPIC_MATRICULA_CONFIRMADA,
        KafkaConfig.TOPIC_MATRICULA_TRASLADADA,
        KafkaConfig.TOPIC_MATRICULA_RETIRADA,
        KafkaConfig.TOPIC_MATRICULA_ANULADA
    }, groupId = "pago-ms")
    @Transactional
    public void procesarEventoMatricula(String mensajeJson) {
        try {
            EventoSobre<MatriculaEvent> sobre = EventoSobre.leer(objectMapper, mensajeJson, MatriculaEvent.class);

            if (sobre.getTraceId() != null) {
                MDC.put("traceId", sobre.getTraceId());
            }

            // 1. Verificación de Idempotencia por eventId
            if (eventoProcesadoRepository.existsByEventId(sobre.getEventId())) {
                log.info("Evento ya procesado previamente: {} (tipo: {}). Descartando.", sobre.getEventId(), sobre.getEventType());
                return;
            }

            // Registrar en eventos_procesados
            eventoProcesadoRepository.save(EventoProcesado.builder()
                .eventId(sobre.getEventId())
                .eventType(sobre.getEventType())
                .procesadoEn(LocalDateTime.now())
                .build());

            MatriculaEvent event = sobre.getPayload();
            log.info("Consumiendo evento {} para matrícula {} del alumno {}", sobre.getEventType(), event.getMatriculaId(), event.getAlumnoId());

            // 2. Sincronizar en réplica local
            matriculaLocalService.sincronizarDesdeEvento(event);

            // 3. Ejecutar lógica según tópico
            switch (sobre.getEventType()) {
                case KafkaConfig.TOPIC_MATRICULA_CREADA -> generadorCuotasService.generarCuotaMatricula(event);
                case KafkaConfig.TOPIC_MATRICULA_CONFIRMADA -> generadorCuotasService.generarCuotasPension(event);
                case KafkaConfig.TOPIC_MATRICULA_RETIRADA, KafkaConfig.TOPIC_MATRICULA_ANULADA ->
                    generadorCuotasService.anularCuotasPorRetiroOAnulacion(event.getMatriculaId());
                default -> log.debug("Evento {} registrado en réplica sin acciones adicionales.", sobre.getEventType());
            }
        } catch (Exception e) {
            log.error("Error al procesar evento de matrícula en pago-ms: {}", e.getMessage(), e);
            throw new RuntimeException("Fallo al consumir evento de matrícula", e);
        } finally {
            MDC.remove("traceId");
        }
    }
}

