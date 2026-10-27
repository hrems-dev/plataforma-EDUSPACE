package pe.edu.upeu.eduspace.asistencia.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import pe.edu.upeu.eduspace.asistencia.config.KafkaConfig;
import pe.edu.upeu.eduspace.asistencia.event.EventoSobre;
import pe.edu.upeu.eduspace.asistencia.event.MatriculaEvent;
import pe.edu.upeu.eduspace.asistencia.service.MatriculaLocalService;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class MatriculaEventConsumer {

    private final MatriculaLocalService service;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = {
        KafkaConfig.TOPIC_MATRICULA_CREADA, KafkaConfig.TOPIC_MATRICULA_CONFIRMADA, KafkaConfig.TOPIC_MATRICULA_TRASLADADA,
        KafkaConfig.TOPIC_MATRICULA_RETIRADA, KafkaConfig.TOPIC_MATRICULA_ANULADA}, groupId = "asistencia-ms")
    public void onMatricula(String json) {
        EventoSobre<MatriculaEvent> sobre;
        try {
            sobre = EventoSobre.leer(objectMapper, json, MatriculaEvent.class);
        } catch (Exception e) {
            log.error("matricula.* mal formado descartado: {}", e.getMessage());
            return;
        }
        if (sobre.getTraceId() != null) MDC.put("traceId", sobre.getTraceId());
        try {
            service.procesar(sobre);
        } finally {
            MDC.remove("traceId");
        }
    }
}
