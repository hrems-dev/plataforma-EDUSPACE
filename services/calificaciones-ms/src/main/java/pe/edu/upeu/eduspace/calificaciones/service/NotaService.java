package pe.edu.upeu.eduspace.calificaciones.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upeu.eduspace.calificaciones.dto.NotaItemRequest;
import pe.edu.upeu.eduspace.calificaciones.dto.NotaRequest;
import pe.edu.upeu.eduspace.calificaciones.dto.NotaResponse;
import pe.edu.upeu.eduspace.calificaciones.dto.NotasLoteRequest;
import pe.edu.upeu.eduspace.calificaciones.entity.CriterioEvaluacion;
import pe.edu.upeu.eduspace.calificaciones.entity.MatriculaLocal;
import pe.edu.upeu.eduspace.calificaciones.entity.Nota;
import pe.edu.upeu.eduspace.calificaciones.exception.BusinessRuleException;
import pe.edu.upeu.eduspace.calificaciones.exception.ResourceNotFoundException;
import pe.edu.upeu.eduspace.calificaciones.repository.CriterioEvaluacionRepository;
import pe.edu.upeu.eduspace.calificaciones.repository.MatriculaLocalRepository;
import pe.edu.upeu.eduspace.calificaciones.repository.NotaRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotaService {

    private final NotaRepository notaRepository;
    private final CriterioEvaluacionRepository criterioRepository;
    private final MatriculaLocalRepository matriculaLocalRepository;
    private final CriterioService criterioService;
    private final PromedioService promedioService;
    private final DocenteAutorizacionService autorizacion;

    @Transactional
    public NotaResponse registrarOActualizarNota(Long notaId, NotaRequest request, Long usuarioId, boolean esAdmin) {
        autorizacion.exigirAsignacion(usuarioId, esAdmin, request.getSeccionId(), request.getCursoId());
        criterioService.validarPeriodoAbierto(request.getSeccionId(), request.getPeriodoId());

        CriterioEvaluacion criterio = criterioRepository.findById(request.getCriterioId())
            .orElseThrow(() -> new ResourceNotFoundException("Criterio no encontrado con ID: " + request.getCriterioId()));

        MatriculaLocal matricula = matriculaLocalRepository.findById(request.getMatriculaId())
            .orElseThrow(() -> new ResourceNotFoundException("Matrícula no encontrada con ID: " + request.getMatriculaId()));

        if (!matricula.getSeccionId().equals(request.getSeccionId())) {
            throw new BusinessRuleException("La matrícula no pertenece a la sección especificada");
        }

        Nota nota;
        if (notaId != null) {
            nota = notaRepository.findById(notaId)
                .orElseThrow(() -> new ResourceNotFoundException("Nota no encontrada con ID: " + notaId));
            nota.setValor(request.getValor());
            nota.setActualizadoEn(LocalDateTime.now());
        } else {
            nota = notaRepository.findByCriterioIdAndMatriculaId(criterio.getId(), matricula.getMatriculaId())
                .orElseGet(() -> Nota.builder()
                    .matriculaId(matricula.getMatriculaId())
                    .alumnoId(matricula.getAlumnoId())
                    .seccionId(request.getSeccionId())
                    .cursoId(request.getCursoId())
                    .periodoId(request.getPeriodoId())
                    .criterio(criterio)
                    .origen(request.getOrigen() != null ? request.getOrigen() : "MANUAL")
                    .origenId(request.getOrigenId())
                    .registradoPor(usuarioId)
                    .registradoEn(LocalDateTime.now())
                    .build());

            nota.setValor(request.getValor());
            nota.setActualizadoEn(LocalDateTime.now());
        }

        Nota guardada = notaRepository.save(nota);

        // Recalcular promedio del alumno para este curso y periodo
        promedioService.calcularYGuardarPromedio(matricula.getMatriculaId(), request.getCursoId(), request.getPeriodoId());

        return toResponse(guardada);
    }

    @Transactional
    public List<NotaResponse> registrarNotasLote(NotasLoteRequest request, Long usuarioId, boolean esAdmin) {
        autorizacion.exigirAsignacion(usuarioId, esAdmin, request.getSeccionId(), request.getCursoId());
        criterioService.validarPeriodoAbierto(request.getSeccionId(), request.getPeriodoId());

        CriterioEvaluacion criterio = criterioRepository.findById(request.getCriterioId())
            .orElseThrow(() -> new ResourceNotFoundException("Criterio no encontrado con ID: " + request.getCriterioId()));

        List<NotaResponse> responses = new ArrayList<>();

        for (NotaItemRequest item : request.getNotas()) {
            MatriculaLocal matricula = matriculaLocalRepository.findById(item.getMatriculaId())
                .orElseThrow(() -> new ResourceNotFoundException("Matrícula no encontrada: " + item.getMatriculaId()));

            if (!matricula.getSeccionId().equals(request.getSeccionId())) {
                throw new BusinessRuleException("El alumno con matrícula " + item.getMatriculaId() + " no pertenece a la sección " + request.getSeccionId());
            }

            Nota nota = notaRepository.findByCriterioIdAndMatriculaId(criterio.getId(), matricula.getMatriculaId())
                .orElseGet(() -> Nota.builder()
                    .matriculaId(matricula.getMatriculaId())
                    .alumnoId(matricula.getAlumnoId())
                    .seccionId(request.getSeccionId())
                    .cursoId(request.getCursoId())
                    .periodoId(request.getPeriodoId())
                    .criterio(criterio)
                    .origen("MANUAL")
                    .registradoPor(usuarioId)
                    .registradoEn(LocalDateTime.now())
                    .build());

            nota.setValor(item.getValor());
            nota.setActualizadoEn(LocalDateTime.now());

            Nota guardada = notaRepository.save(nota);
            promedioService.calcularYGuardarPromedio(matricula.getMatriculaId(), request.getCursoId(), request.getPeriodoId());
            responses.add(toResponse(guardada));
        }

        log.info("Lote de {} notas registradas para criterio id={}, seccionId={}", responses.size(), criterio.getId(), request.getSeccionId());
        return responses;
    }

    public NotaResponse toResponse(Nota n) {
        return NotaResponse.builder()
            .id(n.getId())
            .matriculaId(n.getMatriculaId())
            .alumnoId(n.getAlumnoId())
            .seccionId(n.getSeccionId())
            .cursoId(n.getCursoId())
            .periodoId(n.getPeriodoId())
            .criterioId(n.getCriterio().getId())
            .criterioNombre(n.getCriterio().getNombre())
            .valor(n.getValor())
            .origen(n.getOrigen())
            .origenId(n.getOrigenId())
            .registradoPor(n.getRegistradoPor())
            .registradoEn(n.getRegistradoEn())
            .actualizadoEn(n.getActualizadoEn())
            .build();
    }
}

