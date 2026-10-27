package pe.edu.upeu.eduspace.asistencia.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upeu.eduspace.asistencia.dto.Dtos.CorreccionRequest;
import pe.edu.upeu.eduspace.asistencia.dto.Dtos.DetalleResponse;
import pe.edu.upeu.eduspace.asistencia.entity.AsistenciaAuditoria;
import pe.edu.upeu.eduspace.asistencia.entity.AsistenciaDetalle;
import pe.edu.upeu.eduspace.asistencia.exception.BusinessRuleException;
import pe.edu.upeu.eduspace.asistencia.exception.ResourceNotFoundException;
import pe.edu.upeu.eduspace.asistencia.repository.AuditoriaRepository;
import pe.edu.upeu.eduspace.asistencia.repository.DetalleRepository;

import java.time.LocalDateTime;

/** Corrección de una asistencia ya registrada (también en sesión cerrada): solo ADMIN/SECRETARIA, con motivo y auditoría. */
@Service
@RequiredArgsConstructor
public class CorreccionService {

    private final DetalleRepository detalleRepo;
    private final AuditoriaRepository auditoriaRepo;
    private final RegistroAsistenciaService registro;
    private final MatriculaNombres nombres;

    @Transactional
    public DetalleResponse corregir(Long detalleId, CorreccionRequest req, Long usuarioId) {
        AsistenciaDetalle d = detalleRepo.findById(detalleId)
            .orElseThrow(() -> new ResourceNotFoundException("Detalle de asistencia no encontrado: " + detalleId));
        if ("TARDANZA".equals(req.estado()) && (req.minutosTarde() == null || req.minutosTarde() <= 0)) {
            throw new BusinessRuleException("La tardanza requiere minutosTarde > 0.");
        }
        String anterior = d.getEstado();
        if (anterior.equals(req.estado()) && java.util.Objects.equals(d.getMinutosTarde(), "TARDANZA".equals(req.estado()) ? req.minutosTarde() : null)) {
            throw new BusinessRuleException("La corrección no cambia el estado actual (" + anterior + ").");
        }
        d.setEstado(req.estado());
        d.setMinutosTarde("TARDANZA".equals(req.estado()) ? req.minutosTarde() : null);
        d.setConfirmado(true);
        d.setActualizadoEn(LocalDateTime.now());
        d = detalleRepo.save(d);
        auditoriaRepo.save(AsistenciaAuditoria.builder().detalleId(d.getId()).estadoAnterior(anterior)
            .estadoNuevo(req.estado()).motivo(req.motivo()).usuarioId(usuarioId).build());
        registro.notificarFaltaSiCorresponde(d.getSesion(), d);
        return new DetalleResponse(d.getId(), d.getMatriculaId(), d.getAlumnoId(), nombres.de(d.getMatriculaId(), d.getAlumnoId()),
            d.getEstado(), d.getMinutosTarde(), d.getObservacion(), true);
    }
}
