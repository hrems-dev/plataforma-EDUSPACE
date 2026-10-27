package pe.edu.upeu.eduspace.calificaciones.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upeu.eduspace.calificaciones.client.PlanificacionClient;
import pe.edu.upeu.eduspace.calificaciones.dto.CriterioRequest;
import pe.edu.upeu.eduspace.calificaciones.dto.CriterioResponse;
import pe.edu.upeu.eduspace.calificaciones.dto.ValidarPesosResponse;
import pe.edu.upeu.eduspace.calificaciones.entity.CierrePeriodo;
import pe.edu.upeu.eduspace.calificaciones.entity.CriterioEvaluacion;
import pe.edu.upeu.eduspace.calificaciones.exception.BusinessRuleException;
import pe.edu.upeu.eduspace.calificaciones.exception.ResourceNotFoundException;
import pe.edu.upeu.eduspace.calificaciones.repository.CierrePeriodoRepository;
import pe.edu.upeu.eduspace.calificaciones.repository.CriterioEvaluacionRepository;
import pe.edu.upeu.eduspace.calificaciones.repository.NotaRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CriterioService {

    private final CriterioEvaluacionRepository criterioRepository;
    private final CierrePeriodoRepository cierrePeriodoRepository;
    private final NotaRepository notaRepository;
    private final PlanificacionClient planificacionClient;
    private final NotaEventoService notaEventoService;
    private final DocenteAutorizacionService autorizacion;

    @Transactional
    public CriterioResponse crearCriterio(CriterioRequest request, Long usuarioId, boolean esAdmin) {
        validarPeriodoAbierto(request.getSeccionId(), request.getPeriodoId());

        if (!esAdmin && usuarioId != null) {
            validarAsignacionDocente(usuarioId, request.getSeccionId(), request.getCursoId());
        }

        criterioRepository.findByPeriodoIdAndSeccionIdAndCursoIdAndNombre(
            request.getPeriodoId(), request.getSeccionId(), request.getCursoId(), request.getNombre().trim()
        ).ifPresent(c -> {
            throw new BusinessRuleException("Ya existe un criterio con el nombre '" + request.getNombre() + "' para este curso y periodo");
        });

        BigDecimal sumaActual = criterioRepository.sumPesoByPeriodoAndSeccionAndCurso(
            request.getPeriodoId(), request.getSeccionId(), request.getCursoId()
        );
        if (sumaActual.add(request.getPeso()).compareTo(new BigDecimal("100.00")) > 0) {
            throw new BusinessRuleException(String.format(
                "La suma total de pesos excedería el 100%% (Actual: %.2f%%, Criterio: %.2f%%)",
                sumaActual, request.getPeso()
            ));
        }

        CriterioEvaluacion criterio = CriterioEvaluacion.builder()
            .anioId(request.getAnioId())
            .periodoId(request.getPeriodoId())
            .seccionId(request.getSeccionId())
            .cursoId(request.getCursoId())
            .nombre(request.getNombre().trim())
            .peso(request.getPeso())
            .origenTipo(request.getOrigenTipo() != null ? request.getOrigenTipo().toUpperCase() : "MANUAL")
            .creadoPor(usuarioId)
            .creadoEn(LocalDateTime.now())
            .build();

        CriterioEvaluacion guardado = criterioRepository.save(criterio);
        log.info("Criterio de evaluación creado: id={}, nombre={}, peso={}", guardado.getId(), guardado.getNombre(), guardado.getPeso());
        int aplicadas = notaEventoService.aplicarPendientes(guardado);
        if (aplicadas > 0) log.info("{} nota(s) pendientes aplicadas al nuevo criterio {}", aplicadas, guardado.getId());
        return toResponse(guardado);
    }

    @Transactional
    public CriterioResponse actualizarCriterio(Long id, CriterioRequest request, Long usuarioId, boolean esAdmin) {
        CriterioEvaluacion criterio = criterioRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Criterio no encontrado con ID: " + id));

        validarPeriodoAbierto(criterio.getSeccionId(), criterio.getPeriodoId());

        if (!esAdmin && usuarioId != null) {
            validarAsignacionDocente(usuarioId, criterio.getSeccionId(), criterio.getCursoId());
        }

        // Si cambió de nombre, validar que no choque
        if (!criterio.getNombre().equalsIgnoreCase(request.getNombre().trim())) {
            criterioRepository.findByPeriodoIdAndSeccionIdAndCursoIdAndNombre(
                criterio.getPeriodoId(), criterio.getSeccionId(), criterio.getCursoId(), request.getNombre().trim()
            ).ifPresent(c -> {
                throw new BusinessRuleException("Ya existe un criterio con el nombre '" + request.getNombre() + "'");
            });
        }

        // Validar nueva suma de pesos
        BigDecimal sumaSinActual = criterioRepository.sumPesoByPeriodoAndSeccionAndCurso(
            criterio.getPeriodoId(), criterio.getSeccionId(), criterio.getCursoId()
        ).subtract(criterio.getPeso());

        if (sumaSinActual.add(request.getPeso()).compareTo(new BigDecimal("100.00")) > 0) {
            throw new BusinessRuleException(String.format(
                "La suma total de pesos excedería el 100%% (Actual restante: %.2f%%, Nuevo peso: %.2f%%)",
                sumaSinActual, request.getPeso()
            ));
        }

        criterio.setNombre(request.getNombre().trim());
        criterio.setPeso(request.getPeso());
        if (request.getOrigenTipo() != null) {
            criterio.setOrigenTipo(request.getOrigenTipo().toUpperCase());
        }

        CriterioEvaluacion guardado = criterioRepository.save(criterio);
        return toResponse(guardado);
    }

    @Transactional
    public void eliminarCriterio(Long id, Long usuarioId, boolean esAdmin) {
        CriterioEvaluacion criterio = criterioRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Criterio no encontrado con ID: " + id));

        validarPeriodoAbierto(criterio.getSeccionId(), criterio.getPeriodoId());

        if (!esAdmin && usuarioId != null) {
            validarAsignacionDocente(usuarioId, criterio.getSeccionId(), criterio.getCursoId());
        }

        if (!notaRepository.findByCriterioId(id).isEmpty()) {
            throw new BusinessRuleException("No se puede eliminar el criterio porque ya contiene notas registradas");
        }

        criterioRepository.delete(criterio);
        log.info("Criterio de evaluación eliminado: id={}", id);
    }

    @Transactional(readOnly = true)
    public List<CriterioResponse> listarPorCursoYPeriodo(Long periodoId, Long seccionId, Long cursoId) {
        return criterioRepository.findByPeriodoIdAndSeccionIdAndCursoId(periodoId, seccionId, cursoId)
            .stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public ValidarPesosResponse validarPesos(Long periodoId, Long seccionId, Long cursoId) {
        List<CriterioEvaluacion> lista = criterioRepository.findByPeriodoIdAndSeccionIdAndCursoId(periodoId, seccionId, cursoId);
        BigDecimal suma = lista.stream()
            .map(CriterioEvaluacion::getPeso)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        boolean esValido = suma.compareTo(new BigDecimal("100.00")) == 0;
        String mensaje = esValido
            ? "Los criterios suman exactamente 100.00%"
            : String.format("Los criterios suman %.2f%% (debe ser exactamente 100.00%% para permitir cierre)", suma);

        return ValidarPesosResponse.builder()
            .periodoId(periodoId)
            .seccionId(seccionId)
            .cursoId(cursoId)
            .sumaPesos(suma)
            .esValido(esValido)
            .mensaje(mensaje)
            .criterios(lista.stream().map(this::toResponse).toList())
            .build();
    }

    public void validarPeriodoAbierto(Long seccionId, Long periodoId) {
        cierrePeriodoRepository.findBySeccionIdAndPeriodoId(seccionId, periodoId).ifPresent(c -> {
            if ("CERRADO".equalsIgnoreCase(c.getEstado())) {
                throw new BusinessRuleException(String.format(
                    "El periodo %d de la sección %d se encuentra CERRADO. No se permiten modificaciones.",
                    periodoId, seccionId
                ));
            }
        });
    }

    private void validarAsignacionDocente(Long usuarioId, Long seccionId, Long cursoId) {
        autorizacion.exigirAsignacion(usuarioId, false, seccionId, cursoId);
    }

    public CriterioResponse toResponse(CriterioEvaluacion c) {
        return CriterioResponse.builder()
            .id(c.getId())
            .anioId(c.getAnioId())
            .periodoId(c.getPeriodoId())
            .seccionId(c.getSeccionId())
            .cursoId(c.getCursoId())
            .nombre(c.getNombre())
            .peso(c.getPeso())
            .origenTipo(c.getOrigenTipo())
            .creadoPor(c.getCreadoPor())
            .creadoEn(c.getCreadoEn())
            .build();
    }
}

