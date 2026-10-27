package pe.edu.upeu.eduspace.calificaciones.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upeu.eduspace.calificaciones.client.AlumnosClient;
import pe.edu.upeu.eduspace.calificaciones.dto.BoletinResponse;
import pe.edu.upeu.eduspace.calificaciones.dto.CriterioResponse;
import pe.edu.upeu.eduspace.calificaciones.dto.LibretaAlumnoResponse;
import pe.edu.upeu.eduspace.calificaciones.dto.PromedioResponse;
import pe.edu.upeu.eduspace.calificaciones.dto.RegistroNotasSeccionResponse;
import pe.edu.upeu.eduspace.calificaciones.dto.ResultadoAnualResponse;
import pe.edu.upeu.eduspace.calificaciones.entity.CierrePeriodo;
import pe.edu.upeu.eduspace.calificaciones.entity.CriterioEvaluacion;
import pe.edu.upeu.eduspace.calificaciones.entity.MatriculaLocal;
import pe.edu.upeu.eduspace.calificaciones.entity.Nota;
import pe.edu.upeu.eduspace.calificaciones.entity.PromedioPeriodo;
import pe.edu.upeu.eduspace.calificaciones.entity.ResultadoAnual;
import pe.edu.upeu.eduspace.calificaciones.exception.ResourceNotFoundException;
import pe.edu.upeu.eduspace.calificaciones.repository.BoletinRepository;
import pe.edu.upeu.eduspace.calificaciones.repository.CierrePeriodoRepository;
import pe.edu.upeu.eduspace.calificaciones.repository.CriterioEvaluacionRepository;
import pe.edu.upeu.eduspace.calificaciones.repository.MatriculaLocalRepository;
import pe.edu.upeu.eduspace.calificaciones.repository.NotaRepository;
import pe.edu.upeu.eduspace.calificaciones.repository.PromedioPeriodoRepository;
import pe.edu.upeu.eduspace.calificaciones.repository.ResultadoAnualRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConsultaService {

    private final MatriculaLocalRepository matriculaLocalRepository;
    private final CriterioEvaluacionRepository criterioRepository;
    private final NotaRepository notaRepository;
    private final PromedioPeriodoRepository promedioRepository;
    private final CierrePeriodoRepository cierrePeriodoRepository;
    private final ResultadoAnualRepository resultadoAnualRepository;
    private final BoletinRepository boletinRepository;
    private final CriterioService criterioService;
    private final PromedioService promedioService;
    private final ResultadoAnualService resultadoAnualService;
    private final BoletinService boletinService;
    private final AlumnosClient alumnosClient;

    @Transactional(readOnly = true)
    public RegistroNotasSeccionResponse obtenerRegistroSeccion(Long seccionId, Long cursoId, Long periodoId) {
        String estadoPeriodo = cierrePeriodoRepository.findBySeccionIdAndPeriodoId(seccionId, periodoId)
            .map(CierrePeriodo::getEstado)
            .orElse("ABIERTO");

        List<CriterioResponse> criterios = criterioService.listarPorCursoYPeriodo(periodoId, seccionId, cursoId);
        List<MatriculaLocal> matriculas = matriculaLocalRepository.findBySeccionId(seccionId);
        List<Nota> notas = notaRepository.findByPeriodoIdAndSeccionIdAndCursoId(periodoId, seccionId, cursoId);

        // Agrupar notas por matriculaId -> (criterioId -> valor)
        Map<Long, Map<Long, BigDecimal>> notasPorMatricula = new HashMap<>();
        for (Nota n : notas) {
            notasPorMatricula.computeIfAbsent(n.getMatriculaId(), k -> new HashMap<>())
                .put(n.getCriterio().getId(), n.getValor());
        }

        List<PromedioPeriodo> promedios = promedioRepository.findByPeriodoIdAndSeccionIdAndCursoId(periodoId, seccionId, cursoId);
        Map<Long, PromedioPeriodo> promedioPorMatricula = promedios.stream()
            .collect(Collectors.toMap(PromedioPeriodo::getMatriculaId, p -> p, (a, b) -> a));

        List<RegistroNotasSeccionResponse.FilaRegistroAlumno> filas = new ArrayList<>();
        for (MatriculaLocal m : matriculas) {
            PromedioPeriodo prom = promedioPorMatricula.get(m.getMatriculaId());
            filas.add(RegistroNotasSeccionResponse.FilaRegistroAlumno.builder()
                .matriculaId(m.getMatriculaId())
                .alumnoId(m.getAlumnoId())
                .alumnoNombre(m.getAlumnoNombre())
                .notasPorCriterioId(notasPorMatricula.getOrDefault(m.getMatriculaId(), Collections.emptyMap()))
                .promedio(prom != null ? prom.getPromedio() : null)
                .literal(prom != null ? prom.getLiteral() : null)
                .build());
        }

        return RegistroNotasSeccionResponse.builder()
            .seccionId(seccionId)
            .cursoId(cursoId)
            .periodoId(periodoId)
            .estadoPeriodo(estadoPeriodo)
            .criterios(criterios)
            .alumnos(filas)
            .build();
    }

    @Transactional(readOnly = true)
    public List<PromedioResponse> obtenerPromediosSeccion(Long seccionId, Long periodoId) {
        List<MatriculaLocal> matriculas = matriculaLocalRepository.findBySeccionId(seccionId);
        Map<Long, String> nombresMap = matriculas.stream()
            .collect(Collectors.toMap(MatriculaLocal::getMatriculaId, MatriculaLocal::getAlumnoNombre, (a, b) -> a));

        List<PromedioPeriodo> promedios = promedioRepository.findByPeriodoIdAndSeccionId(periodoId, seccionId);
        return promedios.stream()
            .map(p -> promedioService.toResponse(p, nombresMap.getOrDefault(p.getMatriculaId(), "Alumno " + p.getAlumnoId())))
            .toList();
    }

    @Transactional(readOnly = true)
    public LibretaAlumnoResponse obtenerLibretaAlumno(Long alumnoId, Long anioId, boolean esAlumnoOApoderado) {
        MatriculaLocal matricula;
        if (anioId != null) {
            matricula = matriculaLocalRepository.findByAlumnoIdAndAnioId(alumnoId, anioId)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró matrícula para alumno " + alumnoId + " en año " + anioId));
        } else {
            List<MatriculaLocal> mats = matriculaLocalRepository.findByAlumnoId(alumnoId);
            if (mats.isEmpty()) {
                throw new ResourceNotFoundException("No se encontraron matrículas para alumno con ID: " + alumnoId);
            }
            matricula = mats.get(0);
        }

        List<PromedioPeriodo> promedios = promedioRepository.findByMatriculaId(matricula.getMatriculaId());

        // Regla §3.4 / §3.5: Alumno/apoderado solo ve periodos cerrados
        if (esAlumnoOApoderado) {
            promedios = promedios.stream()
                .filter(p -> "CERRADO".equalsIgnoreCase(p.getEstado()))
                .toList();
        }

        // Agrupar promedios por curso
        Map<Long, List<PromedioPeriodo>> porCurso = promedios.stream()
            .collect(Collectors.groupingBy(PromedioPeriodo::getCursoId));

        List<ResultadoAnual> resultadosAnuales = resultadoAnualRepository.findByMatriculaId(matricula.getMatriculaId());
        Map<Long, ResultadoAnual> resAnualMap = resultadosAnuales.stream()
            .collect(Collectors.toMap(ResultadoAnual::getCursoId, r -> r, (a, b) -> a));

        List<LibretaAlumnoResponse.CursoLibretaDto> cursos = new ArrayList<>();
        for (Map.Entry<Long, List<PromedioPeriodo>> entry : porCurso.entrySet()) {
            Long cursoId = entry.getKey();
            Map<Long, LibretaAlumnoResponse.PeriodoNotaDto> notasPorPeriodo = new HashMap<>();

            for (PromedioPeriodo p : entry.getValue()) {
                notasPorPeriodo.put(p.getPeriodoId(), LibretaAlumnoResponse.PeriodoNotaDto.builder()
                    .periodoId(p.getPeriodoId())
                    .promedio(p.getPromedio())
                    .literal(p.getLiteral())
                    .estado(p.getEstado())
                    .build());
            }

            ResultadoAnual res = resAnualMap.get(cursoId);
            cursos.add(LibretaAlumnoResponse.CursoLibretaDto.builder()
                .cursoId(cursoId)
                .cursoNombre("Curso " + cursoId)
                .notasPorPeriodo(notasPorPeriodo)
                .promedioFinal(res != null ? res.getPromedioFinal() : null)
                .literalFinal(res != null ? res.getLiteral() : null)
                .estadoFinal(res != null ? res.getEstado() : null)
                .build());
        }

        List<BoletinResponse> boletines = boletinRepository.findByMatriculaIdOrderByGeneradoEnDesc(matricula.getMatriculaId())
            .stream()
            .filter(b -> "VIGENTE".equalsIgnoreCase(b.getEstado()))
            .map(boletinService::toResponse)
            .toList();

        return LibretaAlumnoResponse.builder()
            .alumnoId(alumnoId)
            .alumnoNombre(matricula.getAlumnoNombre())
            .matriculaId(matricula.getMatriculaId())
            .anioId(matricula.getAnioId())
            .nivelId(matricula.getNivelId())
            .gradoId(matricula.getGradoId())
            .seccionId(matricula.getSeccionId())
            .cursos(cursos)
            .resultadosAnuales(resultadosAnuales.stream().map(resultadoAnualService::toResponse).toList())
            .boletines(boletines)
            .build();
    }

    @Transactional(readOnly = true)
    public List<LibretaAlumnoResponse> obtenerLibretasDeAlumnos(java.util.Collection<Long> alumnoIds, Long anioId) {
        List<LibretaAlumnoResponse> libretas = new ArrayList<>();
        for (Long alumnoId : alumnoIds) {
            try {
                libretas.add(obtenerLibretaAlumno(alumnoId, anioId, true));
            } catch (Exception e) {
                log.warn("No se pudo obtener libreta del alumno {}: {}", alumnoId, e.getMessage());
            }
        }
        return libretas;
    }
}
