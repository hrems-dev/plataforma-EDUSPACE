package pe.edu.upeu.eduspace.calificaciones.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upeu.eduspace.calificaciones.dto.CierrePeriodoRequest;
import pe.edu.upeu.eduspace.calificaciones.dto.CierrePeriodoResponse;
import pe.edu.upeu.eduspace.calificaciones.dto.ReaperturaRequest;
import pe.edu.upeu.eduspace.calificaciones.entity.CierrePeriodo;
import pe.edu.upeu.eduspace.calificaciones.entity.CriterioEvaluacion;
import pe.edu.upeu.eduspace.calificaciones.entity.MatriculaLocal;
import pe.edu.upeu.eduspace.calificaciones.entity.Nota;
import pe.edu.upeu.eduspace.calificaciones.entity.PromedioPeriodo;
import pe.edu.upeu.eduspace.calificaciones.exception.BusinessRuleException;
import pe.edu.upeu.eduspace.calificaciones.repository.CierrePeriodoRepository;
import pe.edu.upeu.eduspace.calificaciones.repository.CriterioEvaluacionRepository;
import pe.edu.upeu.eduspace.calificaciones.repository.MatriculaLocalRepository;
import pe.edu.upeu.eduspace.calificaciones.repository.NotaRepository;
import pe.edu.upeu.eduspace.calificaciones.repository.PromedioPeriodoRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CierrePeriodoService {

    private final CierrePeriodoRepository cierrePeriodoRepository;
    private final CriterioEvaluacionRepository criterioRepository;
    private final MatriculaLocalRepository matriculaLocalRepository;
    private final NotaRepository notaRepository;
    private final PromedioPeriodoRepository promedioRepository;
    private final PromedioService promedioService;
    private final NotaEventoService notaEventoService;

    @Transactional
    public CierrePeriodoResponse cerrarPeriodo(CierrePeriodoRequest request, Long usuarioId) {
        Long seccionId = request.getSeccionId();
        Long periodoId = request.getPeriodoId();

        CierrePeriodo cierre = cierrePeriodoRepository.findBySeccionIdAndPeriodoId(seccionId, periodoId)
            .orElseGet(() -> CierrePeriodo.builder()
                .seccionId(seccionId)
                .periodoId(periodoId)
                .estado("ABIERTO")
                .build());

        if ("CERRADO".equalsIgnoreCase(cierre.getEstado())) {
            throw new BusinessRuleException("El periodo ya se encuentra CERRADO para esta sección");
        }

        // 1. Obtener todos los cursos que tienen criterios definidos en esta sección y periodo
        List<Long> cursoIds = criterioRepository.findDistinctCursosByPeriodoAndSeccion(periodoId, seccionId);
        if (cursoIds.isEmpty()) {
            throw new BusinessRuleException("No se puede cerrar el periodo: no existen criterios de evaluación configurados en ningún curso");
        }

        // 2. Obtener alumnos matriculados activos
        List<MatriculaLocal> matriculas = matriculaLocalRepository.findBySeccionIdAndEstado(seccionId, "MATRICULADO");
        if (matriculas.isEmpty()) {
            matriculas = matriculaLocalRepository.findBySeccionId(seccionId);
        }

        // 3. Validar para cada curso: suma de pesos = 100% y notas completas
        for (Long cursoId : cursoIds) {
            BigDecimal sumaPesos = criterioRepository.sumPesoByPeriodoAndSeccionAndCurso(periodoId, seccionId, cursoId);
            if (sumaPesos.compareTo(new BigDecimal("100.00")) != 0) {
                throw new BusinessRuleException(String.format(
                    "No se puede cerrar: el curso %d tiene suma de pesos igual a %.2f%% (debe ser exactamente 100%%)",
                    cursoId, sumaPesos
                ));
            }

            List<CriterioEvaluacion> criterios = criterioRepository.findByPeriodoIdAndSeccionIdAndCursoId(periodoId, seccionId, cursoId);

            // Validar que cada alumno tenga nota en cada criterio
            for (MatriculaLocal m : matriculas) {
                for (CriterioEvaluacion c : criterios) {
                    List<Nota> notas = notaRepository.findByMatriculaIdAndCriterioId(m.getMatriculaId(), c.getId());
                    if (notas.isEmpty()) {
                        throw new BusinessRuleException(String.format(
                            "Falta registrar nota en el curso %d, criterio '%s' para el alumno '%s' (matrícula %d)",
                            cursoId, c.getNombre(), m.getAlumnoNombre(), m.getMatriculaId()
                        ));
                    }
                }
                // Asegurar cálculo final del promedio
                promedioService.calcularYGuardarPromedio(m.getMatriculaId(), cursoId, periodoId);
            }
        }

        // 4. Congelar todos los promedios de la sección y periodo a 'CERRADO'
        List<PromedioPeriodo> promedios = promedioRepository.findByPeriodoIdAndSeccionId(periodoId, seccionId);
        for (PromedioPeriodo p : promedios) {
            p.setEstado("CERRADO");
            promedioRepository.save(p);
        }

        // 5. Actualizar estado del cierre de periodo
        cierre.setEstado("CERRADO");
        cierre.setCerradoPor(usuarioId);
        cierre.setCerradoEn(LocalDateTime.now());
        CierrePeriodo guardado = cierrePeriodoRepository.save(cierre);

        log.info("Periodo cerrado exitosamente: seccionId={}, periodoId={}, cerradoPor={}", seccionId, periodoId, usuarioId);
        return toResponse(guardado);
    }

    @Transactional
    public CierrePeriodoResponse reabrirPeriodo(ReaperturaRequest request, Long usuarioId) {
        Long seccionId = request.getSeccionId();
        Long periodoId = request.getPeriodoId();

        CierrePeriodo cierre = cierrePeriodoRepository.findBySeccionIdAndPeriodoId(seccionId, periodoId)
            .orElseThrow(() -> new BusinessRuleException("No existe registro de cierre para este periodo y sección"));

        if (!"CERRADO".equalsIgnoreCase(cierre.getEstado())) {
            throw new BusinessRuleException("El periodo no se encuentra en estado CERRADO, estado actual: " + cierre.getEstado());
        }

        cierre.setEstado("REABIERTO");
        cierre.setReabiertoPor(usuarioId);
        cierre.setReabiertoEn(LocalDateTime.now());
        cierre.setMotivoReapertura(request.getMotivo());
        CierrePeriodo guardado = cierrePeriodoRepository.save(cierre);
        notaEventoService.reprocesarPeriodoReabierto(seccionId, periodoId);

        // Descongelar promedios para permitir recálculo
        List<PromedioPeriodo> promedios = promedioRepository.findByPeriodoIdAndSeccionId(periodoId, seccionId);
        for (PromedioPeriodo p : promedios) {
            p.setEstado("BORRADOR");
            promedioRepository.save(p);
        }

        log.info("Periodo reabierto con éxito: seccionId={}, periodoId={}, motivo='{}'", seccionId, periodoId, request.getMotivo());
        return toResponse(guardado);
    }

    public CierrePeriodoResponse toResponse(CierrePeriodo c) {
        return CierrePeriodoResponse.builder()
            .id(c.getId())
            .seccionId(c.getSeccionId())
            .periodoId(c.getPeriodoId())
            .estado(c.getEstado())
            .cerradoPor(c.getCerradoPor())
            .cerradoEn(c.getCerradoEn())
            .reabiertoPor(c.getReabiertoPor())
            .reabiertoEn(c.getReabiertoEn())
            .motivoReapertura(c.getMotivoReapertura())
            .build();
    }
}

