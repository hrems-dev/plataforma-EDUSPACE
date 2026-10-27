package pe.edu.upeu.eduspace.pago.service;

import lombok.RequiredArgsConstructor;
import pe.edu.upeu.eduspace.pago.client.AlumnosGateway;
import pe.edu.upeu.eduspace.pago.repository.CuotaRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upeu.eduspace.pago.dto.PagoResponse;
import pe.edu.upeu.eduspace.pago.dto.RegistrarPagoRequest;

@Service
@RequiredArgsConstructor
public class PagoOnlineService {

    private final PagoService pagoService;
    private final AlumnosGateway alumnosGateway;
    private final CuotaRepository cuotaRepository;

    @Transactional
    public PagoResponse pagarOnlineSimulado(RegistrarPagoRequest req, Long apoderadoUsuarioId) {
        exigirQueSeaDelApoderado(req, apoderadoUsuarioId);
        req.setMedio("ONLINE_SIMULADO");
        if (req.getReferencia() == null || req.getReferencia().isBlank()) {
            req.setReferencia("SIM-TX-" + System.currentTimeMillis());
        }
        return pagoService.registrarPago(req, apoderadoUsuarioId);
    }

    /** El apoderado solo paga por sus hijos: el alumno declarado debe ser hijo suyo y todas las cuotas, de ese alumno. */
    private void exigirQueSeaDelApoderado(RegistrarPagoRequest req, Long apoderadoUsuarioId) {
        boolean esHijo = alumnosGateway.hijos(apoderadoUsuarioId).stream()
            .anyMatch(h -> h.getAlumnoId().equals(req.getAlumnoId()));
        boolean cuotasPropias = req.getDetalles().stream()
            .allMatch(d -> cuotaRepository.findById(d.getCuotaId()).map(c -> req.getAlumnoId().equals(c.getAlumnoId())).orElse(true));
        if (!esHijo || !cuotasPropias) {
            throw new AccessDeniedException("Solo puede pagar cuotas de sus propios hijos.");
        }
    }
}
