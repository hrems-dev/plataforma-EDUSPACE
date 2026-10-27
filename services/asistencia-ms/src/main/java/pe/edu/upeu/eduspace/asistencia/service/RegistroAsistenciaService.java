package pe.edu.upeu.eduspace.asistencia.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upeu.eduspace.asistencia.config.KafkaConfig;
import pe.edu.upeu.eduspace.asistencia.dto.Dtos.ItemRegistro;
import pe.edu.upeu.eduspace.asistencia.dto.Dtos.RegistroAsistenciaRequest;
import pe.edu.upeu.eduspace.asistencia.dto.Dtos.SesionResponse;
import pe.edu.upeu.eduspace.asistencia.entity.AsistenciaDetalle;
import pe.edu.upeu.eduspace.asistencia.entity.MatriculaLocal;
import pe.edu.upeu.eduspace.asistencia.entity.SesionAsistencia;
import pe.edu.upeu.eduspace.asistencia.event.AsistenciaFaltaRegistradaEvent;
import pe.edu.upeu.eduspace.asistencia.exception.BusinessRuleException;
import pe.edu.upeu.eduspace.asistencia.messaging.AsistenciaEventProducer;
import pe.edu.upeu.eduspace.asistencia.repository.DetalleRepository;
import pe.edu.upeu.eduspace.asistencia.repository.MatriculaLocalRepository;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class RegistroAsistenciaService {

    private final SesionService sesionService;
    private final DetalleRepository detalleRepo;
    private final MatriculaLocalRepository matriculaRepo;
    private final AsistenciaEventProducer producer;

    /** Guarda el lote de toda la sección en UNA transacción. La falta se notifica una sola vez por detalle. */
    @Transactional
    public SesionResponse registrar(Long sesionId, RegistroAsistenciaRequest req, Long usuarioId, boolean esAdmin) {
        SesionAsistencia s = sesionService.obtener(sesionId);
        sesionService.exigirDocenteDe(s, usuarioId, esAdmin);
        if ("CERRADA".equals(s.getEstado())) {
            throw new BusinessRuleException("La sesión está CERRADA; solo ADMIN/SECRETARIA pueden corregirla con motivo.");
        }
        Set<Long> vistos = new HashSet<>();
        for (ItemRegistro it : req.items()) {
            if (!vistos.add(it.matriculaId())) {
                throw new BusinessRuleException("La matrícula " + it.matriculaId() + " aparece repetida en el registro.");
            }
            validarItem(it);
            MatriculaLocal m = matriculaRepo.findById(it.matriculaId())
                .filter(x -> x.getSeccionId().equals(s.getSeccionId()))
                .orElseThrow(() -> new BusinessRuleException("La matrícula " + it.matriculaId() + " no pertenece a la sección de la sesión."));
            AsistenciaDetalle d = detalleRepo.findBySesionIdAndMatriculaId(sesionId, it.matriculaId())
                .orElseGet(() -> AsistenciaDetalle.builder().sesion(s).matriculaId(m.getMatriculaId()).alumnoId(m.getAlumnoId()).estado("PRESENTE").build());
            d.setEstado(it.estado());
            d.setMinutosTarde("TARDANZA".equals(it.estado()) ? it.minutosTarde() : null);
            d.setObservacion(it.observacion());
            d.setConfirmado(true);
            d.setActualizadoEn(LocalDateTime.now());
            d = detalleRepo.save(d);
            notificarFaltaSiCorresponde(s, d);
        }
        return sesionService.hoja(sesionId);
    }

    static void validarItem(ItemRegistro it) {
        if ("TARDANZA".equals(it.estado()) && (it.minutosTarde() == null || it.minutosTarde() <= 0)) {
            throw new BusinessRuleException("La tardanza de la matrícula " + it.matriculaId() + " requiere minutosTarde > 0.");
        }
    }

    /** Publica asistencia.falta-registrada solo la primera vez que el detalle queda en FALTA. */
    void notificarFaltaSiCorresponde(SesionAsistencia s, AsistenciaDetalle d) {
        if (!"FALTA".equals(d.getEstado()) || Boolean.TRUE.equals(d.getFaltaNotificada())) return;
        d.setFaltaNotificada(true);
        detalleRepo.save(d);
        producer.publicar(KafkaConfig.TOPIC_FALTA_REGISTRADA, String.valueOf(d.getId()),
            AsistenciaFaltaRegistradaEvent.builder().asistenciaId(s.getId()).asistenciaDetalleId(d.getId())
                .alumnoId(d.getAlumnoId()).matriculaId(d.getMatriculaId()).seccionId(s.getSeccionId()).fecha(s.getFecha()).build());
    }
}
