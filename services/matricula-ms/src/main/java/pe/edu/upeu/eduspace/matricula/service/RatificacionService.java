package pe.edu.upeu.eduspace.matricula.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pe.edu.upeu.eduspace.matricula.client.PagoClient;
import pe.edu.upeu.eduspace.matricula.client.PlanificacionClient;
import pe.edu.upeu.eduspace.matricula.client.PlanificacionGateway;
import pe.edu.upeu.eduspace.matricula.dto.Dtos.*;
import pe.edu.upeu.eduspace.matricula.entity.Matricula;
import pe.edu.upeu.eduspace.matricula.exception.BusinessRuleException;
import pe.edu.upeu.eduspace.matricula.repository.CupoSeccionRepository;
import pe.edu.upeu.eduspace.matricula.repository.MatriculaRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * Ratificación para el año siguiente (individual o masiva). Cada alumno se procesa en su propia transacción:
 * los que fallan se reportan sin detener a los demás. La decisión de "aprobó" la toma secretaría (MVP).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RatificacionService {

    private final MatriculaRepository matriculaRepository;
    private final MatriculaService matriculaService;
    private final PlanificacionGateway planificacion;
    private final CupoSeccionRepository cupoRepository;
    private final PagoClient pagoClient;

    public RatificacionResultado ratificar(RatificacionRequest req, Long usuarioId) {
        List<Matricula> origen = new ArrayList<>();
        if (req.matriculaAnteriorId() != null) {
            origen.add(matriculaService.obtener(req.matriculaAnteriorId()));
        } else if (req.seccionOrigenId() != null) {
            origen.addAll(matriculaRepository.findBySeccionIdAndEstadoIn(req.seccionOrigenId(), List.of("MATRICULADO")));
        } else {
            throw new BusinessRuleException("Indique matriculaAnteriorId (individual) o seccionOrigenId (masiva).");
        }
        if (req.seccionDestinoId() == null && req.gradoDestinoId() == null) {
            throw new BusinessRuleException("Indique seccionDestinoId o gradoDestinoId para ubicar a los alumnos.");
        }

        List<MatriculaResponse> creadas = new ArrayList<>();
        List<ErrorRatificacion> errores = new ArrayList<>();
        for (Matricula anterior : origen) {
            try {
                creadas.add(ratificarUna(anterior, req, usuarioId));
            } catch (Exception e) {
                log.warn("Ratificación fallida para matrícula {}: {}", anterior.getId(), e.getMessage());
                errores.add(new ErrorRatificacion(anterior.getId(), anterior.getAlumnoId(), e.getMessage()));
            }
        }
        return new RatificacionResultado(origen.size(), creadas.size(), creadas, errores);
    }

    private MatriculaResponse ratificarUna(Matricula anterior, RatificacionRequest req, Long usuarioId) {
        if (!"MATRICULADO".equals(anterior.getEstado())) {
            throw new BusinessRuleException("La matrícula anterior no está MATRICULADO (estado " + anterior.getEstado() + ").");
        }
        if (anterior.getAnioId().equals(req.anioDestinoId())) {
            throw new BusinessRuleException("El año destino debe ser distinto al de la matrícula anterior.");
        }
        PagoClient.Deuda deuda;
        try {
            deuda = pagoClient.deuda(anterior.getAlumnoId());
        } catch (Exception e) {
            throw new BusinessRuleException("No se pudo verificar la deuda del alumno en pago-ms; intente más tarde.");
        }
        if (deuda != null && deuda.tieneDeudaVencida()) {
            throw new BusinessRuleException("El alumno tiene deuda vencida (S/ " + deuda.montoVencido() + "); debe regularizarla.");
        }
        Long seccionDestino = req.seccionDestinoId() != null ? req.seccionDestinoId() : primeraSeccionConCupo(req);
        return matriculaService.crearInterno(anterior.getAlumnoId(), seccionDestino, "RATIFICACION",
            "Ratificación de la matrícula " + anterior.getCodigo(), anterior.getId(), usuarioId);
    }

    private Long primeraSeccionConCupo(RatificacionRequest req) {
        for (PlanificacionClient.Seccion s : planificacion.secciones(req.anioDestinoId(), req.gradoDestinoId())) {
            var cupo = cupoRepository.findById(s.id());
            int libres = cupo.map(c -> c.getCapacidad() - c.getOcupados()).orElse(s.capacidad() == null ? 0 : (int) s.capacidad());
            if (libres > 0) return s.id();
        }
        throw new BusinessRuleException("No hay secciones con cupo en el grado destino.");
    }
}
